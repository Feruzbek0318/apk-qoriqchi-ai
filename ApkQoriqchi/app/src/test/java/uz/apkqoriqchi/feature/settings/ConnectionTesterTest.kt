package uz.apkqoriqchi.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uz.apkqoriqchi.core.data.ApiKey

class ConnectionTesterTest {
    @Test fun `virustotal treats 404 as a working key`() {
        assertThat(ConnectionTester.classify(ApiKey.VIRUSTOTAL, 200)).isEqualTo(TestOutcome.OK)
        assertThat(ConnectionTester.classify(ApiKey.VIRUSTOTAL, 404)).isEqualTo(TestOutcome.OK)
        assertThat(ConnectionTester.classify(ApiKey.VIRUSTOTAL, 401)).isEqualTo(TestOutcome.BAD_KEY)
        assertThat(ConnectionTester.classify(ApiKey.GEMINI, 404)).isEqualTo(TestOutcome.OTHER)
    }

    @Test fun `quota, server and google bad key`() {
        assertThat(ConnectionTester.classify(ApiKey.GEMINI, 429)).isEqualTo(TestOutcome.QUOTA)
        assertThat(ConnectionTester.classify(ApiKey.GEMINI, 529)).isEqualTo(TestOutcome.SERVER)
        assertThat(ConnectionTester.classify(ApiKey.SAFE_BROWSING, 400)).isEqualTo(TestOutcome.BAD_KEY)
        assertThat(ConnectionTester.classify(ApiKey.SAFE_BROWSING, 403)).isEqualTo(TestOutcome.BAD_KEY)
    }

    @Test fun `error message extraction`() {
        assertThat(ConnectionTester.errorMessage("""{"error":{"code":"WrongCredentialsError","message":"Wrong API key"}}"""))
            .isEqualTo("Wrong API key")
        assertThat(ConnectionTester.errorMessage("""{"error":"quota"}""")).isEqualTo("quota")
        assertThat(ConnectionTester.errorMessage("<html>")).isNull()
    }

    @Test fun `gemini bad key (400 API key not valid) is BAD_KEY`() {
        assertThat(ConnectionTester.classify(ApiKey.GEMINI, 400)).isEqualTo(TestOutcome.BAD_KEY)
        assertThat(ConnectionTester.classify(ApiKey.GEMINI, 200)).isEqualTo(TestOutcome.OK)
    }
}
