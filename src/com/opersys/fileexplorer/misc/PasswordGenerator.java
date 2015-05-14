/*
* Copyright (C) 2014-2015, Opersys inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.opersys.fileexplorer.misc;

import java.util.Random;

public class PasswordGenerator {

    public static String NewPassword(int length) {
        String pwdChar = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuffer pwd;
        Random rnd;
        int n;

        pwd = new StringBuffer(length);
        rnd = new Random();

        for (int i = 0; i < length; i++) {
            n = rnd.nextInt(pwdChar.length());
            pwd.append(pwdChar.charAt(n));
        }

        return pwd.toString();
    }
}
