/*
 * Copyright 2015, The Android Open Source Project
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

package com.android.managedprovisioning;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;

import com.android.managedprovisioning.ProvisionLogger;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;

public class LogoUtils {
    public static void saveOrganisationLogo(Context context, Uri uri) {
        final File logoFile = getOrganisationLogoFile(context);
        try {
            final InputStream in = context.getContentResolver().openInputStream(uri);
            final FileOutputStream out = new FileOutputStream(logoFile);
            final byte buffer[] = new byte[1024];
            int bytesReadCount;
            while ((bytesReadCount = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesReadCount);
            }
            out.close();
            ProvisionLogger.logi("Organisation logo from uri " + uri + " has been successfully"
                    + " copied to " + logoFile);
        } catch (IOException e) {
            ProvisionLogger.logi("Could not write organisation logo from " + uri + " to "
                    + logoFile, e);
            // If the file was only partly written, delete it.
            logoFile.delete();
        }
    }

    public static Drawable getOrganisationLogo(Context context) {
        final File logoFile = getOrganisationLogoFile(context);
        try {
            if (logoFile.exists()) {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(),
                        Uri.fromFile(logoFile));
                if (bitmap != null) {
                    ProvisionLogger.logi("Obtained organisation logo from " + logoFile);
                    return new BitmapDrawable(context.getResources(), bitmap);
                } else {
                    ProvisionLogger.loge("Could not get organisation logo from " + logoFile);
                }
            }
        } catch (IOException e) {
            ProvisionLogger.loge("Could not get organisation logo from " + logoFile, e);
        }
        return context.getDrawable(R.drawable.ic_corp_icon);
    }

    public static void cleanUp(Context context) {
        getOrganisationLogoFile(context).delete();
    }

    private static File getOrganisationLogoFile(Context context) {
        return new File(context.getFilesDir() + File.separator + "organisation_logo");
    }
}
