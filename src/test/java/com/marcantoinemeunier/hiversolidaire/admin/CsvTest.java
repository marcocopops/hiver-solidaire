package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** CSV des exports : échappement des cellules et neutralisation des formules (injection dans un tableur). */
class CsvTest {

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://example.org\")", "+33600000001", "-1+1", "@SUM(A1)", "\t=1", "\r=1"})
    void a_cell_that_could_start_a_formula_is_prefixed_with_an_apostrophe(String value) {
        assertThat(Csv.cell(value)).startsWith("\"'");
    }

    @Test
    void quotes_are_doubled_and_every_cell_is_quoted() {
        assertThat(Csv.cell("Anne \"la Grande\"")).isEqualTo("\"Anne \"\"la Grande\"\"\"");
        assertThat(Csv.cell("Martin; Anne")).isEqualTo("\"Martin; Anne\"");
        assertThat(Csv.cell("")).isEqualTo("\"\"");
        assertThat(Csv.cell(null)).isEqualTo("\"\"");
    }

    @Test
    void an_ordinary_cell_is_kept_as_is() {
        assertThat(Csv.cell("Élodie")).isEqualTo("\"Élodie\"");
        assertThat(Csv.cell("2027-01-04")).isEqualTo("\"2027-01-04\"");
    }

    @Test
    void a_row_ends_with_crlf() {
        assertThat(Csv.row(List.of("a", "=b"))).isEqualTo("\"a\";\"'=b\"\r\n");
    }
}
