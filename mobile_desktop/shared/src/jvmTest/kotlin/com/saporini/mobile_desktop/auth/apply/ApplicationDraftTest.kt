package com.saporini.mobile_desktop.auth.apply

import com.saporini.mobile_desktop.auth.ui.apply.ApplicationDraft
import com.saporini.mobile_desktop.auth.ui.apply.applicationProblems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Open a restaurant" form's checks, which match the server's. */
class ApplicationDraftTest {
    private val complete = ApplicationDraft(
        name = "Bistro", legalName = "Bistro LLC", email = "hi@bistro.example", phone = "+355 69 123 4567",
        firstName = "Ana", lastName = "Owner", ownerEmail = "ana@owner.example", username = "ana"
    )

    @Test fun aCompleteApplicationHasNoProblems() {
        assertTrue(applicationProblems(complete).isEmpty(), applicationProblems(complete).toString())
    }

    @Test fun theRestaurantsEmailAndPhoneAreRequiredAndChecked() {
        assertEquals(setOf("email", "phone"), applicationProblems(complete.copy(email = " ", phone = "")).keys)
        assertEquals("This isn't an email address", applicationProblems(complete.copy(email = "not-an-email"))["email"])
        assertEquals(setOf("phone"), applicationProblems(complete.copy(phone = "call me")).keys)
    }

    @Test fun everyRequiredFieldIsNamed() {
        val problems = applicationProblems(ApplicationDraft(currency = "", timezone = ""))
        assertEquals(setOf("name", "legalName", "email", "phone", "currency", "timezone", "firstName", "lastName", "ownerEmail", "username"), problems.keys)
    }

    @Test fun lengthsAndUsernameRulesMatchTheServer() {
        assertTrue("name" in applicationProblems(complete.copy(name = "x".repeat(151))))
        assertTrue("legalName" in applicationProblems(complete.copy(legalName = "x".repeat(201))))
        assertTrue("username" in applicationProblems(complete.copy(username = "a b")))
        assertTrue("ownerPhone" in applicationProblems(complete.copy(ownerPhone = "1".repeat(31))))
        assertNull(applicationProblems(complete.copy(ownerPhone = ""))["ownerPhone"])
    }

    @Test fun theLegalNameAndUsernameFollowUntilTypedByHand() {
        var draft = ApplicationDraft().edit { it.copy(name = "Bistro") }
        assertEquals("Bistro", draft.legalName)
        draft = draft.edit { it.copy(legalName = "Bistro Sh.p.k.") }.edit { it.copy(name = "Bistro Roma") }
        assertEquals("Bistro Sh.p.k.", draft.legalName)

        draft = draft.edit { it.copy(ownerEmail = "ana.owner@x.example") }
        assertEquals("ana.owner", draft.username)
        draft = draft.edit { it.copy(username = "ana", usernameTouched = true) }.edit { it.copy(ownerEmail = "other@x.example") }
        assertEquals("ana", draft.username)
    }

    @Test fun theRequestIsTrimmedAndLeavesOutAnEmptyOwnerPhone() {
        val request = complete.copy(name = "  Bistro ", ownerPhone = "  ").request()
        assertEquals("Bistro", request.name)
        assertNull(request.owner.phone)
        assertEquals("ana", request.owner.username)
    }
}
