package is.hail.expr.ir

import is.hail.annotations.{CodeOrdering, Region, StagedRegionValueBuilder}
import is.hail.expr.types._
import is.hail.asm4s._
import is.hail.utils._

class ArraySorter(mb: EmitMethodBuilder, array: StagedArrayBuilder, keyOnly: Boolean) {
  val typ: Type = array.elt
  val ti: TypeInfo[_] = typeToTypeInfo(typ)
  val sortmb: EmitMethodBuilder = mb.fb.newMethod[Region, Int, Int, Boolean, Unit]

  val (less: CodeOrdering.F[Boolean], greater: CodeOrdering.F[Boolean], equiv: CodeOrdering.F[Boolean]) = if (keyOnly) {
    val ttype = coerce[TBaseStruct](typ)
    require(ttype.size == 2)
    val kt = ttype.types(0)
    val mk1s = sortmb.newLocal[Boolean]
    val mk2s = sortmb.newLocal[Boolean]

    val clt: CodeOrdering.F[Boolean] = {
      case (r1: Code[Region], (m1: Code[Boolean] @unchecked, v1: Code[Long] @unchecked),
            r2: Code[Region], (m2: Code[Boolean] @unchecked, v2: Code[Long] @unchecked)) =>
        val mk1 = Code(mk1s := ttype.isFieldMissing(r1, v1, 0), mk1s)
        val mk2 = Code(mk2s := ttype.isFieldMissing(r2, v2, 0), mk2s)
        val k1 = mk1s.mux(defaultValue(kt), r1.loadIRIntermediate(kt)(ttype.fieldOffset(v1, 0)))
        val k2 = mk2s.mux(defaultValue(kt), r2.loadIRIntermediate(kt)(ttype.fieldOffset(v2, 0)))
        val cmp = mb.getCodeOrdering[Int](kt, CodeOrdering.compare, missingGreatest = true)(r1, (mk1, k1), r2, (mk2, k2))
        !m1 && (m2 || cmp < 0)
    }

    val cgt: CodeOrdering.F[Boolean] = {
      case (r1: Code[Region], (m1: Code[Boolean] @unchecked, v1: Code[Long] @unchecked),
            r2: Code[Region], (m2: Code[Boolean] @unchecked, v2: Code[Long] @unchecked)) =>
        val mk1 = Code(mk1s := ttype.isFieldMissing(r1, v1, 0), mk1s)
        val mk2 = Code(mk2s := ttype.isFieldMissing(r2, v2, 0), mk2s)
        val k1 = mk1s.mux(defaultValue(kt), r1.loadIRIntermediate(kt)(ttype.fieldOffset(v1, 0)))
        val k2 = mk2s.mux(defaultValue(kt), r2.loadIRIntermediate(kt)(ttype.fieldOffset(v2, 0)))
        val cmp = mb.getCodeOrdering[Int](kt, CodeOrdering.compare, missingGreatest = false)(r1, (mk1, k1), r2, (mk2, k2))
        !m1 && (m2 || cmp > 0)
    }

    val ceq: CodeOrdering.F[Boolean] = {
      val mk1l = mb.newLocal[Boolean]
      val mk2l = mb.newLocal[Boolean]

      { case (r1: Code[Region], (m1: Code[Boolean]@unchecked, v1: Code[Long]@unchecked),
              r2: Code[Region]@unchecked, (m2: Code[Boolean]@unchecked, v2: Code[Long]@unchecked)) =>
          val mk1 = Code(mk1l := m1 || ttype.isFieldMissing(r1, v1, 0), mk1l)
          val mk2 = Code(mk2l := m2 || ttype.isFieldMissing(r2, v2, 0), mk2l)
          val k1 = mk1l.mux(defaultValue(kt), r1.loadIRIntermediate(kt)(ttype.fieldOffset(v1, 0)))
          val k2 = mk2l.mux(defaultValue(kt), r2.loadIRIntermediate(kt)(ttype.fieldOffset(v2, 0)))

          mb.getCodeOrdering[Boolean](kt, CodeOrdering.equiv, missingGreatest = true)(r1, (mk1, k1), r2, (mk2, k2))
      }
    }

    (clt, cgt, ceq)
  } else
    (mb.getCodeOrdering[Boolean](typ, CodeOrdering.lt, missingGreatest = true),
      mb.getCodeOrdering[Boolean](typ, CodeOrdering.gt, missingGreatest = false),
      mb.getCodeOrdering[Boolean](typ, CodeOrdering.equiv, missingGreatest = true))

  def sort(ascending: Code[Boolean]): Code[Unit] = {
    val region = sortmb.getArg[Region](1)
    val start = sortmb.getArg[Int](2)
    val end = sortmb.getArg[Int](3)
    val asc = sortmb.getArg[Boolean](4)

    val pi: LocalRef[Int] = sortmb.newLocal[Int]
    val i: LocalRef[Int] = sortmb.newLocal[Int]

    val m1: LocalRef[Boolean] = sortmb.newLocal[Boolean]
    val v1: LocalRef[_] = sortmb.newLocal(ti)

    def loadPivot(start: Code[Int], end: Code[Int]): Code[Unit] = {
      def median(v1: Code[Int], v2: Code[Int], v3: Code[Int]): Code[Int] = {
        lt(v1, v2).mux(
          lt(v2, v3).mux(v2, lt(v1, v3).mux(v3, v1)), // v1 < v2
          lt(v1, v3).mux(v1, lt(v2, v3).mux(v3, v2))) // "v2 < v1"
      }

      val threshold = 10
      val findPivot = (end - start < threshold).mux(
        Code._empty,
        Code(
          pi := (start + end) / 2,
          pi := median(start, pi, end),
          pi.ceq(end).mux(
            Code._empty,
            swap(pi, end))))
      Code(findPivot, pi := end)
    }

    def lt(i: Code[Int], j: Code[Int]): Code[Boolean] =
      asc.mux(
        less(region, (array.isMissing(i), array(i)), region, (array.isMissing(j), array(j))),
        greater(region, (array.isMissing(i), array(i)), region, (array.isMissing(j), array(j))))

    def swap(i: Code[Int], j: Code[Int]): Code[Unit] =
      Code(
        m1 := array.isMissing(i),
        v1.storeAny(array(i)),
        array.setMissing(i, array.isMissing(j)),
        array.isMissing(i).mux(Code._empty, array.update(i, array(j))),
        array.setMissing(j, m1),
        m1.mux(Code._empty, array.update(j, v1)))

    sortmb.emit(Code(
      loadPivot(start, end),
      i := start,
      Code.whileLoop(i < pi,
        lt(pi, i).mux(
          Code(
            i.ceq(pi - 1).mux(
              Code(swap(i, pi), i += 1),
              Code(swap(pi, pi - 1), swap(i, pi))),
            pi += -1),
          i += 1)),
      (start < pi - 1).mux(sortmb.invoke(region, start, pi - 1, asc), Code._empty),
      (pi + 1 < end).mux(sortmb.invoke(region, pi + 1, end, asc), Code._empty)))

    sortmb.invoke(mb.getArg[Region](1), 0, array.size - 1, ascending)
  }

  def toRegion(): Code[Long] = {
    val srvb = new StagedRegionValueBuilder(mb, TArray(typ))
    Code(
      srvb.start(array.size),
      Code.whileLoop(srvb.arrayIdx < array.size,
        array.isMissing(srvb.arrayIdx).mux(
          srvb.setMissing(),
          srvb.addIRIntermediate(typ)(array(srvb.arrayIdx))),
        srvb.advance()),
      srvb.end())
  }

  def distinctFromSorted(): Code[Unit] = {
    def ceq(m1: Code[Boolean], v1: Code[_], m2: Code[Boolean], v2: Code[_]): Code[Boolean] = {
      equiv(mb.getArg[Region](1), (m1, v1), mb.getArg[Region](1), (m2, v2))
    }

    val i = mb.newLocal[Int]
    val n = mb.newLocal[Int]

    val removeMissing = Code(i := array.size - 1,
      Code.whileLoop(i >= 0 && array.isMissing(i), i += -1),
      array.size.ceq(i + 1).mux(Code._empty, array.setSize(i + 1)))

    Code(
      if (keyOnly) removeMissing else Code._empty,
      n := 0,
      i := 0,
      Code.whileLoop(i < array.size,
        Code.whileLoop(i < array.size && ceq(array.isMissing(n), array(n), array.isMissing(i), array(i)),
          i += 1),
        (i < array.size && i.cne(n + 1)).mux(
          Code(
            array.setMissing(n + 1, array.isMissing(i)),
            array.isMissing(n + 1).mux(
              Code._empty,
              array.update(n + 1, array(i)))),
          Code._empty),
        n += 1),
      array.setSize(n))
  }

  def sortIntoRegion(ascending: Code[Boolean], distinct: Boolean): Code[Long] = {
    Code(sort(ascending), if (distinct) distinctFromSorted() else Code._empty, toRegion())
  }
}
