package com.ritvik.passvault.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordImportTest {
    @Test
    fun csvHandlesQuotesCommasNewlinesAndCrlf() {
        val rows = CsvParser.parse("a,b,c\r\n\"x,1\",\"he said \"\"hi\"\"\",\"line1\nline2\"\r\n\r\nlast,,\r\n")
        assertEquals(
            listOf(
                listOf("a", "b", "c"),
                listOf("x,1", "he said \"hi\"", "line1\nline2"),
                listOf("last", "", ""),
            ),
            rows,
        )
    }

    @Test
    fun csvWithoutTrailingNewlineAndWithBomAndSemicolons() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvParser.parse("﻿a;b\n1;2"))
    }

    @Test
    fun chromeExport() {
        val p = PasswordImport.parse(
            "name,url,username,password,note\n" +
                "GitHub,https://github.com/login,me@x.com,\"p,w\"\"1\",my note\n" +
                "Empty,https://e.com,u,,\n",
        )
        assertEquals("Chrome / Edge / Brave", p.source)
        assertEquals(1, p.logins.size)
        assertEquals(1, p.skipped)
        val l = p.logins[0]
        assertEquals("GitHub", l.title)
        assertEquals("https://github.com/login", l.url)
        assertEquals("me@x.com", l.username)
        assertEquals("p,w\"1", l.password)
        assertEquals("my note", l.notes)
    }

    @Test
    fun bitwardenSkipsNonLoginsAndUsesItsColumns() {
        val p = PasswordImport.parse(
            "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
                ",,login,Mail,,,0,https://mail.com,bob,s3cret,\n" +
                ",,note,Secret note,text,,0,,,,\n" +
                ",,card,Visa,,,0,,,,\n",
        )
        assertEquals("Bitwarden", p.source)
        assertEquals(1, p.logins.size)
        assertEquals(2, p.skipped)
        assertEquals("bob", p.logins[0].username)
        assertEquals("s3cret", p.logins[0].password)
    }

    @Test
    fun firefoxAndLastPassAndOnePassword() {
        val ff = PasswordImport.parse("url,username,password,httpRealm,formActionOrigin,guid\nhttps://a.com,u,p,,https://a.com,{1}\n")
        assertEquals("Firefox", ff.source)
        assertEquals("a.com", ff.logins[0].title) // no title column: falls back to the site name

        val lp = PasswordImport.parse("url,username,password,totp,extra,name,grouping,fav\nhttps://b.com,u,p,,note,Bee,Work,0\n")
        assertEquals("LastPass", lp.source)
        assertEquals("Bee", lp.logins[0].title)
        assertEquals("note", lp.logins[0].notes)

        val op = PasswordImport.parse("Title,Url,Username,Password,Notes,Type\nOne,https://c.com,u,p,n,Login\n")
        assertEquals("1Password", op.source)
        assertEquals("One", op.logins[0].title)
    }

    @Test
    fun missingPasswordColumnIsRejected() {
        assertThrows(ImportFormatException::class.java) { PasswordImport.parse("name,url\nx,y\n") }
        assertThrows(ImportFormatException::class.java) { PasswordImport.parse("name,password\n") }
    }

    @Test
    fun tooManyRowsIsRejected() {
        val big = "name,password\n" + (1..5001).joinToString("\n") { "n$it,p$it" }
        assertThrows(ImportFormatException::class.java) { PasswordImport.parse(big) }
    }

    @Test
    fun duplicatesAreRemoved() {
        val logins = PasswordImport.parse(
            "name,url,username,password\nA,https://github.com,me,pw\nB,github.com/login,ME,pw\nC,https://github.com,me,other\nD,https://x.com,u,p\n",
        ).logins
        val result = PasswordImport.withoutDuplicates(logins, listOf(Triple("https://x.com", "u", "p")))
        assertEquals(listOf("A", "C"), result.map { it.title })
    }
}
