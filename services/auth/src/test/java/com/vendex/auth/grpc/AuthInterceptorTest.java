package com.vendex.auth.grpc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInterceptorTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("bearerCases")
    void stripBearer_handles_well_known_shapes(String label, String header, String expected) {
        assertThat(AuthInterceptor.stripBearer(header)).isEqualTo(expected);
    }

    static Stream<Arguments> bearerCases() {
        return Stream.of(
                // happy path + scheme casing
                Arguments.of("plain Bearer", "Bearer abc.def.ghi", "abc.def.ghi"),
                Arguments.of("lowercase bearer", "bearer abc.def.ghi", "abc.def.ghi"),
                Arguments.of("uppercase BEARER", "BEARER abc.def.ghi", "abc.def.ghi"),
                Arguments.of("mixed-case BeArEr", "BeArEr abc.def.ghi", "abc.def.ghi"),

                // malformed: no scheme separator
                Arguments.of("no space", "Bearerabc.def.ghi", null),

                // wrong scheme
                Arguments.of("Basic auth", "Basic dXNlcjpwYXNz", null),

                // empty credentials
                Arguments.of("empty after Bearer", "Bearer ", null),
                Arguments.of("only whitespace after Bearer", "Bearer    ", null),

                // double bearer / extra fields — must NOT be accepted
                Arguments.of("Bearer Bearer xyz", "Bearer Bearer xyz", null),
                Arguments.of("credentials with space", "Bearer abc def", null),

                // null / blank
                Arguments.of("null header", null, null),
                Arguments.of("empty header", "", null),
                Arguments.of("blank header", "   ", null)
        );
    }
}
