.. _GTEx_transcripts:

GTEx_transcripts
================

*  **Versions:** v7
*  **Reference genome builds:** GRCh37, GRCh38
*  **Type:** :class:`MatrixTable`

Schema (v7, GRCh37)
~~~~~~~~~~~~~~~~~~~

.. code-block:: text

    ----------------------------------------
    Global fields:
        'metadata': struct {
            name: str, 
            version: str, 
            reference_genome: str, 
            n_rows: int32, 
            n_cols: int32, 
            n_partitions: int32
        } 
    ----------------------------------------
    Column fields:
        'col_id': str 
        'sample_id': str 
        'SMATSSCR': int32 
        'SMCENTER': str 
        'SMPTHNTS': str 
        'SMRIN': float64 
        'SMTS': str 
        'SMTSD': str 
        'SMUBRID': str 
        'SMTSISCH': int32 
        'SMTSPAX': int32 
        'SMNABTCH': str 
        'SMNABTCHT': str 
        'SMNABTCHD': str 
        'SMGEBTCH': str 
        'SMGEBTCHD': str 
        'SMGEBTCHT': str 
        'SMAFRZE': str 
        'SMGTC': str 
        'SME2MPRT': float64 
        'SMCHMPRS': int32 
        'SMNTRART': float64 
        'SMNUMGPS': int32 
        'SMMAPRT': float64 
        'SMEXNCRT': float64 
        'SM550NRM': float64 
        'SMGNSDTC': int32 
        'SMUNMPRT': float64 
        'SM350NRM': float64 
        'SMRDLGTH': int32 
        'SMMNCPB': float64 
        'SME1MMRT': float64 
        'SMSFLGTH': int32 
        'SMESTLBS': int32 
        'SMMPPD': int32 
        'SMNTERRT': float64 
        'SMRRNANM': int32 
        'SMRDTTL': str 
        'SMVQCFL': int32 
        'SMMNCV': float64 
        'SMTRSCPT': int32 
        'SMMPPDPR': int32 
        'SMCGLGTH': int32 
        'SMGAPPCT': float64 
        'SMUNPDRD': int32 
        'SMNTRNRT': float64 
        'SMMPUNRT': float64 
        'SMEXPEFF': float64 
        'SMMPPDUN': int32 
        'SME2MMRT': float64 
        'SME2ANTI': int32 
        'SMALTALG': int32 
        'SME2SNSE': int32 
        'SMMFLGTH': int32 
        'SME1ANTI': int32 
        'SMSPLTRD': int32 
        'SMBSMMRT': float64 
        'SME1SNSE': int32 
        'SME1PCTS': float64 
        'SMRRNART': float64 
        'SME1MPRT': float64 
        'SMNUM5CD': int32 
        'SMDPMPRT': float64 
        'SME2PCTS': float64 
    ----------------------------------------
    Row fields:
        'transcript_id': str 
        'gene_id': str 
        'transcript_name': str 
        'transcript_type': str 
        'strand': str 
        'transcript_status': str 
        'havana_transcript_id': str 
        'ccdsid': str 
        'ont': str 
        'gene_name': str 
        'interval': interval<locus<GRCh37>> 
        'gene_type': str 
        'annotation_source': str 
        'havana_gene_id': str 
        'gene_status': str 
        'tag': str 
    ----------------------------------------
    Entry fields:
        'read_count': int32 
        'TPM': float64 
    ----------------------------------------
    Column key: ['sample_id']
    Row key: ['transcript_id']
    ----------------------------------------
    
