package me.sproutos.client

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sign-in, tested on the JVM.
 *
 * Everything security-critical here avoids `android.util.Base64` and `android.net.Uri` for exactly
 * this reason: those are framework classes with no implementation in a unit test, so every call
 * returns null and every assertion passes against nothing.
 */
class AuthTest {
    @Test
    fun `sends a challenge, never the verifier`() {
        /*
          PKCE's whole value. A `plain` challenge is the verifier, so anyone who intercepts the
          authorization request has what they need to redeem the code — and this is a public client,
          so there is no secret standing behind it.
        */
        val pending = beginAuth()
        val url = authorizeUrl("https://api.sproutos.me", "sproutos-android", pending)

        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge="))
        assertTrue("the verifier must not be in the URL", !url.contains(pending.verifier))
    }

    @Test
    fun `the challenge is the sha256 of the verifier`() {
        val pending = beginAuth()

        val expected =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray()),
                )

        assertEquals(expected, challengeFor(pending.verifier))
    }

    @Test
    fun `every attempt gets its own verifier and state`() {
        // Reused across attempts, either one stops being a defence: a state that repeats can be
        // replayed, and a verifier that repeats can be captured once and used later.
        val first = beginAuth()
        val second = beginAuth()

        assertNotEquals(first.verifier, second.verifier)
        assertNotEquals(first.state, second.state)
    }

    @Test
    fun `refuses a redirect whose state does not match`() {
        /*
          The attack `state` exists to catch: a redirect that did not come from the flow this app
          started. It is its own result rather than folded into "denied", because a person declining
          and an injected callback are different events and only one of them is worth alarming about.
        */
        val pending = beginAuth()
        val forged = "sproutos://auth/callback?code=stolen&state=not-the-one"

        assertEquals(CallbackResult.StateMismatch, readCallback(forged, pending))
    }

    @Test
    fun `refuses a redirect when no flow was started`() {
        // Nothing pending means this app did not ask for it.
        val result = readCallback("sproutos://auth/callback?code=x&state=y", null)

        assertEquals(CallbackResult.StateMismatch, result)
    }

    @Test
    fun `checks the state before it looks at the code`() {
        /*
          Order matters. If the code were read first, an implementation that forgot the comparison
          would still appear to work — which is how the check gets dropped in a refactor.
        */
        val pending = beginAuth()
        val noCode = "sproutos://auth/callback?state=wrong"

        // No code at all, and the answer is still about the state.
        assertEquals(CallbackResult.StateMismatch, readCallback(noCode, pending))
    }

    @Test
    fun `reads a good callback`() {
        val pending = beginAuth()
        val uri = "sproutos://auth/callback?code=abc123&state=${pending.state}"

        val result = readCallback(uri, pending)

        assertTrue(result is CallbackResult.Code)
        assertEquals("abc123", (result as CallbackResult.Code).code)
        // The verifier travels with the code, because the exchange needs both.
        assertEquals(pending.verifier, result.verifier)
    }

    @Test
    fun `reports a refusal from the platform`() {
        val pending = beginAuth()
        val uri = "sproutos://auth/callback?error=access_denied&state=${pending.state}"

        val result = readCallback(uri, pending)

        assertTrue(result is CallbackResult.Denied)
        assertEquals("access_denied", (result as CallbackResult.Denied).reason)
    }

    @Test
    fun `sends the verifier and no secret when exchanging the code`() {
        val body = tokenRequestBody("abc123", "the-verifier", "sproutos-android")

        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code_verifier=the-verifier"))
        // A public client has no secret. Anything compiled into this APK is readable by anyone who
        // downloads it, so shipping one would be shipping a secret that is not.
        assertTrue("no client secret may be sent", !body.contains("client_secret"))
    }

    @Test
    fun `reads a token and refuses a response that has none`() {
        assertEquals("t0ken", parseToken("""{"access_token":"t0ken","token_type":"Bearer"}"""))
        assertNull(parseToken("""{"access_token":""}"""))
        assertNull(parseToken("{}"))
        assertNull(parseToken("not json"))
    }

    @Test
    fun `decodes a redirect whose values were encoded`() {
        val pending = beginAuth()
        // The state is base64url and can contain `-` and `_`; a code can contain anything.
        val encoded =
            "sproutos://auth/callback?code=a%2Bb%2Fc&state=${java.net.URLEncoder.encode(pending.state, "UTF-8")}"

        val result = readCallback(encoded, pending)

        assertTrue(result is CallbackResult.Code)
        assertEquals("a+b/c", (result as CallbackResult.Code).code)
    }
}
