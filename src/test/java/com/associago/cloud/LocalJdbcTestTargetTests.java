package com.associago.cloud;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalJdbcTestTargetTests {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "postgresql|jdbc:postgresql://127.0.0.1:5432/suite_validation",
        "postgresql|jdbc:postgresql://[::1]:5432/associago_test?sslmode=disable",
        "mysql|jdbc:mysql://127.0.0.1:3306/associago_regression",
        "mysql|jdbc:mysql://[::1]:3306/associago_test?useSSL=false"
    })
    void acceptsOnlyExplicitLoopbackTargets(String engine, String url) {
        assertThat(LocalJdbcTestTarget.requireLoopback(url, engine)).isEqualTo(url);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "postgresql|jdbc:postgresql://production.supabase.co:5432/postgres",
        "postgresql|jdbc:postgresql://localhost:5432/test",
        "postgresql|jdbc:postgresql://10.0.0.7:5432/test",
        "postgresql|jdbc:postgresql://[2001:db8::1]:5432/test",
        "postgresql|jdbc:postgresql://127.0.0.1:5432,production.example.org:5432/test",
        "postgresql|jdbc:postgresql://127.0.0.1:5432/test?PGHOST=production.example.org",
        "postgresql|jdbc:postgresql://127.0.0.1:5432/test?%50GHOST=production.example.org",
        "postgresql|jdbc:postgresql://127.0.0.1:5432/test?service=production",
        "postgresql|jdbc:postgresql://127.0.0.1:5432/",
        "postgresql|jdbc:postgresql://127.0.0.1:5432/test#host=production",
        "postgresql|jdbc:postgresql://user:password@127.0.0.1:5432/test",
        "mysql|jdbc:mysql://production.aivencloud.com:3306/defaultdb",
        "mysql|jdbc:mysql://localhost:3306/test",
        "mysql|jdbc:mysql://127.0.0.1:3306,production.example.org:3306/test",
        "mysql|jdbc:mysql:loadbalance://127.0.0.1:3306,production.example.org:3306/test",
        "mysql|jdbc:mysql://127.0.0.1:3306/test?socketFactory=custom.RemoteFactory",
        "mysql|jdbc:mysql://127.0.0.1:3306/test?socksProxyHost=production.example.org",
        "mysql|jdbc:mysql://127.0.0.1:3306/test?dnsSrv=true",
        "mysql|jdbc:postgresql://127.0.0.1:5432/test"
    })
    void rejectsRemoteAmbiguousAndReroutedTargetsWithoutConnecting(String engine, String url) {
        assertThatThrownBy(() -> LocalJdbcTestTarget.requireLoopback(url, engine))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("literal loopback")
                .hasMessageNotContaining("production.supabase.co").hasMessageNotContaining("password@");
    }
}
