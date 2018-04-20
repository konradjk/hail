package is.hail.expr.ir

import is.hail.expr.ir.functions.{IRFunctionRegistry, IRFunctionWithMissingness, IRFunctionWithoutMissingness}
import is.hail.expr.types._

object Infer {
  def apply(ir: IR, tAgg: Option[TAggregable] = None) { apply(ir, tAgg, new Env[Type]()) }

  def apply(ir: IR, tAgg: Option[TAggregable], env: Env[Type]) {
    def infer(ir: IR, tAgg: Option[TAggregable] = tAgg, env: Env[Type] = env) { apply(ir, tAgg, env) }
    ir match {
      case I32(x) =>
      case I64(x) =>
      case F32(x) =>
      case F64(x) =>
      case Str(x) =>
      case True() =>
      case False() =>

      case Cast(v, typ) =>
        infer(v)
        assert(Casts.valid(v.typ, typ))

      case NA(t) =>
        assert(t != null)
      case IsNA(v) =>
        infer(v)

      case x@If(cond, cnsq, altr, _) =>
        infer(cond)
        infer(cnsq)
        infer(altr)
        assert(cond.typ.isOfType(TBoolean()))
        assert(cnsq.typ == altr.typ, s"${cnsq.typ}, ${altr.typ}, $cond")
        x.typ = cnsq.typ

      case x@Let(name, value, body, _) =>
        infer(value)
        infer(body, env = env.bind(name, value.typ))
        x.typ = body.typ
      case x@Ref(_, _) =>
        x.typ = env.lookup(x)
      case x@ApplyBinaryPrimOp(op, l, r, _) =>
        infer(l)
        infer(r)
        x.typ = BinaryOp.getReturnType(op, l.typ, r.typ)
      case x@ApplyUnaryPrimOp(op, v, _) =>
        infer(v)
        x.typ = UnaryOp.getReturnType(op, v.typ)
      case x@MakeArray(args, typ) =>
        if (args.length == 0)
          assert(typ != null)
        else {
          args.foreach(infer(_))
          val t = args.head.typ
          args.map(_.typ).zipWithIndex.tail.foreach { case (x, i) => assert(x.isOfType(t), s"at position $i type mismatch: $t $x") }
          x.typ = TArray(t)
        }
      case x@ArraySort(a) =>
        infer(a)
        assert(a.typ.isInstanceOf[TArray])
      case x@ArrayRef(a, i, _) =>
        infer(a)
        infer(i)
        assert(i.typ.isOfType(TInt32()))
        x.typ = -coerce[TArray](a.typ).elementType
      case ArrayLen(a) =>
        infer(a)
        assert(a.typ.isInstanceOf[TArray])
      case x@ArrayRange(a, b, c) =>
        infer(a)
        infer(b)
        infer(c)
        assert(a.typ.isOfType(TInt32()))
        assert(b.typ.isOfType(TInt32()))
        assert(c.typ.isOfType(TInt32()))
      case x@ArrayMap(a, name, body, _) =>
        infer(a)
        val tarray = coerce[TArray](a.typ)
        infer(body, env = env.bind(name, tarray.elementType))
        x.elementTyp = body.typ
      case x@ArrayFilter(a, name, cond) =>
        infer(a)
        val tarray = coerce[TArray](a.typ)
        infer(cond, env = env.bind(name, tarray.elementType))
        assert(cond.typ.isOfType(TBoolean()))
      case x@ArrayFlatMap(a, name, body) =>
        infer(a)
        val tarray = coerce[TArray](a.typ)
        infer(body, env = env.bind(name, tarray.elementType))
        assert(body.typ.isInstanceOf[TArray])
      case x@ArrayFold(a, zero, accumName, valueName, body, _) =>
        infer(a)
        val tarray = coerce[TArray](a.typ)
        infer(zero)
        infer(body, env = env.bind(accumName -> zero.typ, valueName -> tarray.elementType))
        assert(body.typ == zero.typ)
        x.typ = zero.typ
      case x@AggIn(typ) =>
        (tAgg, typ) match {
          case (Some(t), null) => x.typ = t
          case (Some(t), t2) => assert(t == t2)
          case (None, _) => throw new RuntimeException("must provide type of aggregable to Infer")
        }
      case x@AggMap(a, name, body, _) =>
        infer(a)
        val tagg = coerce[TAggregable](a.typ)
        infer(body, env = aggScope(tagg).bind(name, tagg.elementType))
        val tagg2 = tagg.copy(elementType = body.typ)
        tagg2.symTab = tagg.symTab
        x.typ = tagg2
      case x@AggFilter(a, name, body, typ) =>
        infer(a)
        val tagg = coerce[TAggregable](a.typ)
        infer(body, env = aggScope(tagg).bind(name, tagg.elementType))
        assert(body.typ.isInstanceOf[TBoolean])
        x.typ = tagg
      case x@AggFlatMap(a, name, body, typ) =>
        infer(a)
        val tagg = coerce[TAggregable](a.typ)
        infer(body, env = aggScope(tagg).bind(name, tagg.elementType))
        val tout = coerce[TArray](body.typ)
        val tagg2 = tagg.copy(elementType = tout.elementType)
        tagg2.symTab = tagg.symTab
        x.typ = tagg2
      case x@ApplyAggOp(a, op, args, _) =>
        infer(a)
        args.foreach(infer(_))
        val tAgg = coerce[TAggregable](a.typ)
        x.typ = AggOp.getType(op, tAgg.elementType, args.map(_.typ))
      case x@MakeStruct(fields, _) =>
        fields.foreach { case (name, a) => infer(a) }
        x.typ = TStruct(fields.map { case (name, a) =>
          (name, a.typ)
        }: _*)
      case x@InsertFields(old, fields, _) =>
        infer(old)
        fields.foreach { case (name, a) => infer(a) }
        x.typ = fields.foldLeft(old.typ){ case (t, (name, a)) =>
          t match {
            case t2: TStruct =>
              t2.selfField(name) match {
                case Some(f2) => t2.updateKey(name, f2.index, a.typ)
                case None => t2.appendKey(name, a.typ)
              }
            case _ => TStruct(name -> a.typ)
          }
        }.asInstanceOf[TStruct]
      case x@GetField(o, name, _) =>
        infer(o)
        val t = coerce[TStruct](o.typ)
        assert(t.index(name).nonEmpty, s"$name not in $t")
        x.typ = -t.field(name).typ
      case x@MakeTuple(types, _) =>
        types.foreach { a => infer(a) }
        x.typ = TTuple(types.map(_.typ): _*)
      case x@GetTupleElement(o, idx, _) =>
        infer(o)
        val t = coerce[TTuple](o.typ)
        assert(idx >= 0 && idx < t.size)
        x.typ = -t.types(idx)
      case In(i, typ) =>
        assert(typ != null)
      case Die(msg) =>
      case x@Apply(fn, args, impl) =>
        args.foreach(infer(_))
        if (impl == null)
          x.implementation = IRFunctionRegistry.lookupFunction(fn, args.map(_.typ)).get.asInstanceOf[IRFunctionWithoutMissingness]
        x.implementation.argTypes.foreach(_.clear())
        assert(args.map(_.typ).zip(x.implementation.argTypes).forall { case (i, j) => j.unify(i) })
      case x@ApplySpecial(fn, args, impl) =>
        args.foreach(infer(_))
        if (impl == null)
          x.implementation = IRFunctionRegistry.lookupFunction(fn, args.map(_.typ)).get.asInstanceOf[IRFunctionWithMissingness]
        x.implementation.argTypes.foreach(_.clear())
        assert(args.map(_.typ).zip(x.implementation.argTypes).forall {case (i, j) => j.unify(i)})
      case x@TableAggregate(child, query, _) =>
        infer(query, tAgg = Some(child.typ.tAgg), env = child.typ.aggEnv)
        x.typ = query.typ
      case MatrixWrite(_, _, _, _) =>
      case TableWrite(_, _, _, _) =>
      case TableCount(_) =>
    }
  }

  private def aggScope(t: TAggregable): Env[Type] =
    Env.empty.bind(t.bindings:_*)
}
