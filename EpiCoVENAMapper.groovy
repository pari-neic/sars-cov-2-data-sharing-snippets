import java.util.function.Function

class EpiCoVENAMapper {

    static Map<String, String> sample(EpiCoVRecord epicov) {
        sample(epicov, [:])
    }

    static Map<String, String> sample(EpiCoVRecord epicov, Map<String, Function<EpiCoVRecord, String>> ext) {
        def mapping = sample_mapping + ext
        applyMapping(mapping, epicov)
    }

    static Map<String, String> genome(String study_alias, EpiCoVRecord epicov) {
        genome(epicov, [study: (it) -> { study_alias }])
    }

    static Map<String, String> genome(EpiCoVRecord epicov, Map<String, Function<EpiCoVRecord, String>> ext) {
        def mapping = genome_mapping + ext
        applyMapping(mapping, epicov)
    }

    static Map<String, String> applyMapping(Map<String, Function<EpiCoVRecord, String>> mapping, EpiCoVRecord epicov) {
        if(epicov.covv_host.toLowerCase().trim() !="human")
            throw new IllegalArgumentException("The mapping is only valid for human hosts")

        mapping.collectEntries {
            [it.key, it.value.apply(epicov)]
        }.findAll {
            it.value
        }
    }

    final static Map<String, Function<EpiCoVRecord, String>> sample_mapping = [
            // Descriptive elements defined by ENA
            'sample_alias': (it) -> { it.name().identifier },
            'tax_id': (it) -> { '2697049' },
            'scientific_name': (it) -> { 'Severe acute respiratory syndrome coronavirus 2' },
            'sample_title': (it) -> { it.covv_virus_name }, // Very short summary of the distinguishing characteristics of the sample
            'sample_description': (it) -> { it.covv_virus_name }, // Summary and reference to specimen collection / storage / sample extraction protocols

            // Descriptive elements defined by the Pathogen reporting checklist (ERC000033)
            'host scientific name': (it) -> { 'Homo sapiens' },
            'host common name': (it) -> { 'Human' },
            'geographic location (country and/or sea)': (it) -> { it.locations().country ?: it.locations().continent },
            'geographic location (region and locality)': (it) -> {
                    [it.locations().region, it.locations().location, it.covv_add_location].findAll().join(', ') ?: null
            },
            'sample capture status': (it) -> { 'active surveillance in response to outbreak' },
            'collecting institution': (it) -> { "$it.covv_orig_lab, $it.covv_orig_lab_addr" },
            'collector name': (it) -> { 'restricted access' },
            'collection date': (it) -> { it.covv_collection_date },
            'isolation source host-associated': (it) -> { it.covv_specimen }, //'Nasal Cavity','Mucus',
            //'receipt date':                                 {it.covv_collection_date},
            'host subject id': (it) -> { 'restricted access' },
            'host health state': (it) -> { it.covv_patient_status ==~ /(?i)hospitali(s|z)ed/ ? "diseased" : null },
            'host age': (it) -> { it.guessAge()?.number ?: 'restricted access' },
            'host sex': (it) -> { it.covv_gender },
            'hospitalisation': (it) -> { it.covv_patient_status ==~ /(?i)hospitali(s|z)ed/ ? "yes" : null },
            'host description': (it) -> {
                ('' + (!it.covv_patient_status.matches(/^(?i)hospitali(s|z)ed$/) ?"Patient status: $it.covv_patient_status. " : '')
                        + (it.covv_add_host_info ? "Additional host info: $it.covv_add_host_info. " : '')
                        + (it.covv_treatment ? "Treatment: $it.covv_treatment. " : '')
                )
            },
            'isolate': (it) -> { it.covv_virus_name },
            'virus identifier': (it) -> { it.covv_provider_sample_id },
            'GISAID Accession ID': (it) -> { it.covv_accession_id },

            // Custom fields that cannot be directly mapped to the Pathogen reporting checklist
            'passage history': (it) -> { it.covv_passage },
            'outbreak': (it) -> { it.covv_outbreak },
            'host age[unit]': (it) -> { it.guessAge()?.unit },
            'date_of_sars_cov_2_vaccination': (it) -> { it.covv_last_vaccinated },
            'authors': (it) -> { it.covv_authors }
    ]

    final static Map<String, Function<EpiCoVRecord, String>> genome_mapping = [
        study: (it) -> { throw new IllegalStateException("ENA study alias / accession must be provided") },
        sample: (it) -> { it.name().identifier },
        name: (it) -> { it.name().identifier },
        description: (it) -> {
            ('' + (it.covv_seq_technology ? "<p>Sequencing technology: $it.covv_seq_technology</p>" : '')
                    + (it.covv_assembly_method ? "<p>Assembly method: $it.covv_assembly_method</p>" : '')
                    + (it.covv_coverage ? "<p>Coverage: $it.covv_coverage</p>" : '')
            )
        },
        // Descriptive elements for an assembled genome in ENA
        assembly_type: (it) -> { 'COVID-19 outbreak' },
        moleculeType: (it) -> { 'genomic RNA' },
        platform: (it) -> { it.guessSequencingPlatform()?: 'not provided' },
        program: (it) -> { 'not provided' },
        coverage: (it) -> {
            String coverage = it.guessCoverageNumber()
            if(!coverage?.toInteger()?.intValue()) {
                throw new IllegalStateException("Estimated base coverage must be provided")
            } else {
                coverage
            }
        },
        authors: (it) -> { it.covv_authors },
        address: (it) -> { "$it.covv_subm_lab, $it.covv_subm_lab_addr" },
        sequence: (it) -> { it.sequence }
    ]
}
