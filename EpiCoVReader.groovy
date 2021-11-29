import groovy.transform.MapConstructor
import org.apache.commons.csv.CSVFormat

import java.nio.file.Path
import java.nio.file.Paths

class EpiCoVReader {
    static List<EpiCoVRecord> fromCsv(Path csv_file) {
        CSVFormat csv_format = CSVFormat.EXCEL.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setAllowDuplicateHeaderNames(true)
            .setAllowMissingColumnNames(true)
            .setDelimiter(',')
            .setTrim(true)
            .setNullString('unknown')
            .build()

        Path current_working_dir = Paths.get('.')
        Path csv_dir = csv_file.parent
        def loadFastaWithCache = this::loadFasta.memoize()
        def resolveFastaWithCache = this::resolveFasta.memoize()

        csv_file.newReader('UTF-8')
            .with { csv_format.parse(it) }
            .collect { it.toMap() }
            .collect { new EpiCoVRecord(it) }
            .each {
                def fasta_path = resolveFastaWithCache(it.fn, csv_dir, current_working_dir)
                def sequence_map = loadFastaWithCache(fasta_path)
                it.sequence = sequence_map.get(it.covv_virus_name)
            }
    }

    static Path resolveFasta(String file_name, Path ...folders) {
        for(def folder in folders) {
            def fasta_file = folder.resolve(file_name)
            if(fasta_file.exists() && fasta_file.isFile()) {
                return fasta_file
            }
        }
        throw new FileNotFoundException("Unable to resolve $file_name in any of $folders")
    }

    static Map<String,String> loadFasta(Path fasta_file) {
        String header = ""
        Map<String, String> sequences = [:]
        for (def line in fasta_file.readLines()) {
            def is_header = line.trim().startsWith('>')
            if (is_header) {
                header = line.substring(1)
                sequences[header] = ""
            } else {
                sequences[header] = sequences[header] + line
            }
        }
        sequences
    }
}


@MapConstructor(pre = {
    args = args.findAll {k,v -> v?.toLowerCase() != 'unknown' && v?.trim() !== ''}
})
class EpiCoVRecord  {
    // Columns defined by the GISAID EpiCoV batch submissions tool

    String submitter, fn, covv_virus_name, covv_type, covv_passage, covv_collection_date, covv_location,
           covv_add_location, covv_host, covv_gender, covv_patient_age, covv_patient_status, covv_seq_technology,
           covv_coverage, covv_orig_lab, covv_orig_lab_addr, covv_subm_lab, covv_subm_lab_addr, covv_subm_sample_id,
           covv_authors, covv_provider_sample_id, covv_specimen, covv_outbreak, covv_add_host_info,
           covv_last_vaccinated, covv_treatment, covv_assembly_method, covv_accession_id

    // The sequence loaded from the corresponding fasta file
    String sequence

    // Computed property objects
    VirusName nameParts() {
        def parts = covv_virus_name.split('/')
        if(parts.size()==4) {
            new VirusName([
                    host      : 'human',
                    country   : parts[1],
                    identifier: parts[2],
                    year      : parts[3]
            ])
        } else {
            new VirusName([
                    host      : parts[1],
                    country   : parts[2],
                    identifier: parts[3],
                    year      : parts[4]
            ])
        }
    }

    Location locations() {
        def locations = covv_location.split(/\s*\/\s*/)
        new Location([
                continent: locations[0],
                country: locations.size()>=2?locations[1]:null,
                region: locations.size()>=3?locations[2]:null,
                location: locations.size()>=4?locations[3]:null
        ])
    }

    String guessCoverageNumber() {
        def coverage_matcher = covv_coverage =~ /(\d+)\s?x/
        if (coverage_matcher.find() && coverage_matcher.getCount() > 0) {
            coverage_matcher.group(0)
        } else {
            null
        }
    }

    String guessSequencingPlatform() {
        if (!covv_seq_technology) return null
        def technology = covv_seq_technology.toLowerCase()
        if (technology.contains('illumina')) {
            'ILLUMINA'
        } else if (['nanopore', 'oxford'].any(technology::contains)) {
            'OXFORD_NANOPORE'
        } else {
            null
        }
    }

    Age guessAge() {
        if(!covv_patient_age) return null
        def matcher = covv_patient_age.trim() =~ /(?<lower>\d+)(?<upper>[\-–]\d+)?(?<unit>\s?\w+)?/
        matcher.find()
        new Age([
                is_range: matcher.group('upper')? true: false,
                number: matcher.group('upper')?
                        "${matcher.group('lower')}-${matcher.group('upper').substring(1)}"
                        : matcher.group('lower'),
                unit: matcher.group('unit')?.trim()?.toLowerCase()?:'years'
        ])

    }

    @MapConstructor
    static class VirusName {
        String host, country, identifier, year
    }

    @MapConstructor
    static class Location {
        String continent, country, region, location
    }

    @MapConstructor
    static class Age {
        String is_range, number, unit
    }

}