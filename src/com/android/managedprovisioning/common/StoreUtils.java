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
import android.content.ComponentName;
import android.os.BaseBundle;
import android.os.PersistableBundle;
import android.util.Base64;
import java.io.IOException;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

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
     * Reads an account from a {@link PersistableBundle}.
     */
    public static Account persistableBundleToAccount(PersistableBundle bundle) {
        return new Account(
                bundle.getString(ATTR_ACCOUNT_NAME),
                bundle.getString(ATTR_ACCOUNT_TYPE));
    }

    /**
     * Writes an account to a {@link PersistableBundle}.
     */
    public static PersistableBundle accountToPersistableBundle(Account account) {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ATTR_ACCOUNT_NAME, account.name);
        bundle.putString(ATTR_ACCOUNT_TYPE, account.type);
        return bundle;
    }

    /**
     * Serialize ComponentName.
     */
    public static String componentNameToString(ComponentName componentName) {
        return componentName == null ? null
                : componentName.getPackageName() + "/" + componentName.getClassName();
    }

    /**
     * Deserialize ComponentName.
     * Don't use {@link ComponentName#unflattenFromString(String)}, because it doesn't keep
     * original class name
     */
    public static ComponentName stringToComponentName(String str) {
        int sep = str.indexOf('/');
        if (sep < 0 || (sep+1) >= str.length()) {
            return null;
        }
        String pkg = str.substring(0, sep);
        String cls = str.substring(sep+1);
        return new ComponentName(pkg, cls);
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

    public static void putIntegerIfNotNull(PersistableBundle bundle, String attrName,
            Integer integer) {
        if (integer != null) {
            bundle.putInt(attrName, integer);
        }
    }

    public static void putPersistableBundlableIfNotNull(PersistableBundle bundle, String attrName,
            PersistableBundlable bundlable) {
        if (bundlable != null) {
            bundle.putPersistableBundle(attrName, bundlable.toPersistableBundle());
        }
    }

    public static <E> E getObjectAttrFromPersistableBundle(PersistableBundle bundle,
            String attrName, Function<PersistableBundle, E> converter) {
        final PersistableBundle attrBundle = bundle.getPersistableBundle(attrName);
        return attrBundle == null ? null : converter.apply(attrBundle);
    }

    public static <E> E getStringAttrFromPersistableBundle(PersistableBundle bundle,
            String attrName, Function<String, E> converter) {
        final String str = bundle.getString(attrName);
        return str == null ? null : converter.apply(str);
    }

    public static Integer getIntegerAttrFromPersistableBundle(PersistableBundle bundle,
            String attrName) {
        return bundle.containsKey(attrName) ? bundle.getInt(attrName) : null;
    }
}
