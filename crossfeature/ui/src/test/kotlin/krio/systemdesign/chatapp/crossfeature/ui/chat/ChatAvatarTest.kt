package krio.systemdesign.chatapp.crossfeature.ui.chat

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

class ChatAvatarTest {

    @Test
    fun `a one-word title gives one letter`() {
        assertThat(initials("anna")).isEqualTo("A")
    }

    @Test
    fun `a longer title gives the letters of its first two words`() {
        assertThat(initials("Project  team alpha")).isEqualTo("PT")
    }

    @Test
    fun `a title's colour is fixed, not picked per run`() {
        assertThat(avatarColorIndex("Anna")).isEqualTo(0)
        assertThat(avatarColorIndex("Max")).isEqualTo(4)
    }

    @Test
    fun `the users get different colours`() {
        val anna = avatarColorIndex("Anna")

        assertThat(avatarColorIndex("Max")).isNotEqualTo(anna)
        assertThat(avatarColorIndex("Kate")).isNotEqualTo(anna)
        assertThat(avatarColorIndex("Kate")).isNotEqualTo(avatarColorIndex("Max"))
    }
}
