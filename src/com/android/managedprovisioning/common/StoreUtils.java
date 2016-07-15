/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.common;

import android.accounts.Account;
import android.util.Base64;
import java.io.IOException;
import java.util.IllformedLocaleException;
import java.util.Locale;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

/**
 * Class with Utils methods to store values in xml files, and to convert various
 * types to and from string.
 */
public class StoreUtils {
    public static final String ATTR_VALUE = "value";

    private static final String ATTR_ACCOUNT_NAME = "account-name";
    private static final String ATTR_ACCOUNT_TYPE = "account-type";

    /**
     * Adds a tag with the given value to an XmlSerializer.
     */
    public static void writeTag(XmlSerializer serializer, String tag, String value)
            throws IOException {
        if (value != null) {
            serializer.startTag(null, tag);
            serializer.attribute(null, ATTR_VALUE, value);
            serializer.endTag(null, tag);
        }
    }

    /**
     * Reads an account from an XmlParser.
     */
    public static Account readAccount(XmlPullParser parser) {
        return new Account(
                parser.getAttributeValue(null, ATTR_ACCOUNT_NAME),
                parser.getAttributeValue(null, ATTR_ACCOUNT_TYPE));
    }

    /**
     * Writes an account to an XmlSerializer.
     */
    public static void writeAccount(XmlSerializer serializer, String tag, Account account)
            throws IOException {
        if (account != null) {
            serializer.startTag(null, tag);
            serializer.attribute(null, ATTR_ACCOUNT_NAME, account.name);
            serializer.attribute(null, ATTR_ACCOUNT_TYPE, account.type);
            serializer.endTag(null, tag);
        }
    }

    /**
     * Converts a String to a Locale.
     */
    public static Locale stringToLocale(String string) throws IllformedLocaleException {
        if (string != null) {
            return new Locale.Builder().setLanguageTag(string.replace("_", "-")).build();
        } else {
            return null;
        }
    }

    /**
     * Converts a Locale to a String.
     */
    public static String localeToString(Locale locale) {
        if (locale != null) {
            return locale.getLanguage() + "_" + locale.getCountry();
        } else {
            return null;
        }
    }

    /**
     * Transforms a string into a byte array.
     *
     * @param s the string to be transformed
     */
    public static byte[] stringToByteArray(String s)
        throws NumberFormatException {
        try {
            return Base64.decode(s, Base64.URL_SAFE);
        } catch (IllegalArgumentException e) {
            throw new NumberFormatException("Incorrect format. Should be Url-safe Base64 encoded.");
        }
    }

    /**
     * Transforms a byte array into a string.
     *
     * @param bytes the byte array to be transformed
     */
    public static String byteArrayToString(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}
