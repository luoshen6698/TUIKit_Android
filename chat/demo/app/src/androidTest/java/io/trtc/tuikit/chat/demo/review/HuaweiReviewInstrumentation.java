package io.trtc.tuikit.chat.demo.review;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Exercises the installed signed APK's real registration form and production response. */
public class HuaweiReviewInstrumentation extends Instrumentation {
    private final Instrumentation instrumentation = this;
    private Bundle arguments;
    private volatile Activity resumedActivity;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            String mode = arguments.getString("mode", "registration");
            if (!("reopen".equals(mode) || "validation".equals(mode) ||
                    ("registration".equals(mode) && arguments.containsKey("round")))) {
                throw new IllegalArgumentException("Specify a review mode or registration round explicitly");
            }
            ((Application) getTargetContext().getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityResumed(Activity activity) { resumedActivity = activity; }
                public void onActivityCreated(Activity activity, Bundle state) {}
                public void onActivityStarted(Activity activity) {}
                public void onActivityPaused(Activity activity) {}
                public void onActivityStopped(Activity activity) {}
                public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                public void onActivityDestroyed(Activity activity) {}
            });
            String expectedChannel = arguments.getString("expectedChannel");
            if (expectedChannel != null) {
                android.content.pm.ApplicationInfo info = getTargetContext().getPackageManager()
                        .getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA);
                assertTrue("Installed APK channel mismatch", expectedChannel.equals(info.metaData.getString("com.xuyinfang.distribution.channel")));
            }
            if ("true".equals(arguments.getString("fresh"))) prepareFirstLaunch();
            if ("reopen".equals(arguments.getString("mode"))) {
                resumedActivity = null;
                Intent launch = new Intent().setClassName(packageName, "io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity");
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                instrumentation.startActivitySync(launch);
                awaitHome();
                SystemClock.sleep(2500);
                capture("cold-reopen");
            } else {
                numericAccountWithOmittedNicknameRegisters();
            }
            result.putString("stream", "Registration regression passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("Expected true");
    }

    private static void assertTrue(String message, boolean condition) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertNotNull(Object value) {
        if (value == null) throw new AssertionError("Expected a value");
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
    private final String packageName = "com.xuyinfang.app";

    private int id(String name) {
        return instrumentation.getTargetContext().getResources().getIdentifier(name, "id", packageName);
    }

    private Activity open(String activity) {
        Intent intent = new Intent().setClassName(packageName, activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (activity.endsWith("XingDunLaunchActivity")) {
            intent.putExtra("xingdun_disable_auto_restore", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        return instrumentation.startActivitySync(intent);
    }

    private void text(Activity activity, String name, String value) {
        instrumentation.runOnMainSync(() -> ((EditText) activity.findViewById(id(name))).setText(value));
    }

    private File proofDirectory() {
        String name = arguments.getString("proofDirectory", "huawei-review");
        assertTrue("Invalid evidence directory", name.matches("[A-Za-z0-9_-]{1,64}"));
        return new File(getTargetContext().getExternalFilesDir(null), name);
    }

    private void prepareFirstLaunch() throws Exception {
        open("io.trtc.tuikit.chat.demo.xingdun.launch.XingDunStartupActivity");
        String acceptLabel = getTargetContext().getString(getTargetContext().getResources()
                .getIdentifier("xingdun_first_privacy_accept", "string", packageName));
        long deadline = SystemClock.uptimeMillis() + 45000;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity current = resumedActivity;
            if (current != null && (current.getClass().getName().endsWith("XingDunLaunchActivity") ||
                    current.getClass().getName().endsWith("MainActivity"))) return;
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null && packageName.contentEquals(String.valueOf(root.getPackageName()))) {
                for (AccessibilityNodeInfo button : root.findAccessibilityNodeInfosByViewId("android:id/button1")) {
                    if (acceptLabel.contentEquals(String.valueOf(button.getText()))) {
                        capture("first-launch-privacy");
                        assertTrue("Privacy acceptance did not respond", button.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                    }
                }
            }
            SystemClock.sleep(250);
        }
        capture("first-launch-failed");
        fail("First launch did not reach the authentication screen");
    }

    private void capture(String name) throws Exception {
        File directory = proofDirectory();
        assertTrue(directory.exists() || directory.mkdirs());
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }


    private View findDescription(View view, String description) {
        if (description.contentEquals(String.valueOf(view.getContentDescription()))) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findDescription(group.getChildAt(index), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String fieldError(Activity activity, String name) {
        View input = activity.findViewById(id(name));
        ViewParent parent = input.getParent();
        while (parent instanceof ViewGroup) {
            TextView error = ((ViewGroup) parent).findViewById(id("textinput_error"));
            if (error != null && error.getVisibility() == View.VISIBLE) return error.getText().toString();
            parent = parent.getParent();
        }
        return "";
    }

    private void click(Activity activity, String name) {
        final boolean[] enabled = {false};
        long deadline = SystemClock.uptimeMillis() + 30000;
        while (!enabled[0] && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> enabled[0] = activity.findViewById(id(name)).isEnabled());
            if (!enabled[0]) SystemClock.sleep(200);
        }
        assertTrue("Action disabled: " + name, enabled[0]);
        final boolean[] clicked = {false};
        instrumentation.runOnMainSync(() -> clicked[0] = activity.findViewById(id(name)).performClick());
        assertTrue("Action did not handle click: " + name, clicked[0]);
    }

    private void awaitHome() {
        final String[] feedback = {""};
        long deadline = SystemClock.uptimeMillis() + 45000;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity current = resumedActivity;
            if (current != null && current.getClass().getName().equals("io.trtc.tuikit.chat.demo.main.MainActivity")) return;
            if (current != null) instrumentation.runOnMainSync(() -> {
                TextView snackbar = current.findViewById(id("snackbar_text"));
                if (snackbar != null && snackbar.getVisibility() == View.VISIBLE) {
                    feedback[0] = snackbar.getText().toString();
                }
            });
            SystemClock.sleep(200);
        }
        fail("Authentication did not reach the real home activity. Displayed feedback: " + feedback[0]);
    }

    public void numericAccountWithOmittedNicknameRegisters() throws Exception {
        String round = arguments.getString("round", "1");
        String username = "19900" + String.valueOf(System.currentTimeMillis()).substring(7);
        String password = "Review_" + UUID.randomUUID().toString().substring(0, 8) + "!";
        String registrationClass = "io.trtc.tuikit.chat.demo.xingdun.launch.XingDunRegistrationActivity";
        String featureClass = "io.trtc.tuikit.chat.demo.main.MainActivity";
        open("io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity");
        SystemClock.sleep(2500);
        Activity registration = open(registrationClass);
        assertTrue("Registration form closed before loading the enterprise", !registration.isFinishing());
        text(registration, "xingdun_registration_username", username);
        text(registration, "xingdun_registration_password", password);
        text(registration, "xingdun_registration_confirm_password", password);
        text(registration, "xingdun_registration_nickname", "");
        instrumentation.runOnMainSync(() -> ((CheckBox) registration.findViewById(id("xingdun_registration_consent"))).setChecked(true));
        if ("validation".equals(arguments.getString("mode"))) {
            text(registration, "xingdun_registration_confirm_password", "Mismatch_123!");
            click(registration, "xingdun_registration_primary_action");
            final boolean[] mismatch = {false};
            instrumentation.runOnMainSync(() -> mismatch[0] = !fieldError(registration, "xingdun_registration_confirm_password").isEmpty());
            assertTrue("Password mismatch did not mark confirmation field", mismatch[0]);
            text(registration, "xingdun_registration_confirm_password", password);
            text(registration, "xingdun_registration_nickname", username);
            click(registration, "xingdun_registration_primary_action");
            final String[] nicknameError = {""};
            long errorDeadline = SystemClock.uptimeMillis() + 30000;
            while (nicknameError[0].isEmpty() && SystemClock.uptimeMillis() < errorDeadline) {
                SystemClock.sleep(200);
                instrumentation.runOnMainSync(() -> {
                    CharSequence value = fieldError(registration, "xingdun_registration_nickname");
                    if (value != null) nicknameError[0] = value.toString();
                });
            }
            int rejectedId = getTargetContext().getResources().getIdentifier("xingdun_nickname_rejected", "string", packageName);
            assertTrue("Nickname rejection did not show the safe specific message", getTargetContext().getString(rejectedId).equals(nicknameError[0]));
            capture("nickname-rejected");
            text(registration, "xingdun_registration_nickname", "");
        }

        instrumentation.runOnMainSync(() -> {
            View action = registration.findViewById(id("xingdun_registration_primary_action"));
            assertTrue("Registration action is disabled", action.isEnabled());
            assertTrue("Registration listener was not invoked", action.performClick());
        });
        Activity opened = null;
        final String[] feedback = {""};
        long deadline = SystemClock.uptimeMillis() + 45000;
        while (opened == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(500);
            Activity current = resumedActivity;
            if (current != null && current.getClass().getName().equals(featureClass)) opened = current;
            instrumentation.runOnMainSync(() -> {
                TextView snackbar = registration.findViewById(id("snackbar_text"));
                if (snackbar != null) feedback[0] = snackbar.getText().toString();
            });
        }

        SystemClock.sleep(2500);
        capture("registration-round-" + round);
        if (opened == null) {
            fail("Registration did not reach home. Displayed feedback: " + feedback[0]);
        }
        assertNotNull(opened);
        if ("validation".equals(arguments.getString("mode"))) {
            Activity homeActivity = opened;
            Intent profile = new Intent().setClassName(packageName, featureClass);
            profile.putExtra("xingdun.target.tab", "profile");
            profile.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            instrumentation.runOnMainSync(() -> homeActivity.startActivity(profile));
            SystemClock.sleep(700);
            String logoutLabel = getTargetContext().getString(getTargetContext().getResources().getIdentifier("md_logout", "string", packageName));
            final View[] logout = {null};
            instrumentation.runOnMainSync(() -> {
                logout[0] = findDescription(homeActivity.getWindow().getDecorView(), logoutLabel);
                if (logout[0] != null) {
                    logout[0].requestRectangleOnScreen(new android.graphics.Rect(0, 0, logout[0].getWidth(), logout[0].getHeight()), true);
                    logout[0].performClick();
                }
            });
            assertNotNull(logout[0]);
            SystemClock.sleep(500);
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            java.util.List<AccessibilityNodeInfo> buttons = root.findAccessibilityNodeInfosByViewId("android:id/button1");
            assertTrue("Logout confirmation not shown", !buttons.isEmpty());
            assertTrue("Logout confirmation failed", buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
            Activity login = null;
            long loginDeadline = SystemClock.uptimeMillis() + 30000;
            while (SystemClock.uptimeMillis() < loginDeadline) {
                Activity current = resumedActivity;
                if (current != null && current.getClass().getName().endsWith("XingDunLaunchActivity")) { login = current; break; }
                SystemClock.sleep(200);
            }
            assertNotNull(login);
            SystemClock.sleep(2500);
            Activity loginForm = login;
            text(loginForm, "xingdun_username", username);
            text(loginForm, "xingdun_password", password);
            instrumentation.runOnMainSync(() -> ((CheckBox) loginForm.findViewById(id("xingdun_privacy_consent"))).setChecked(true));
            resumedActivity = null;
            click(loginForm, "xingdun_primary_action");
            awaitHome();
            capture("login-after-registration");
        }
        File account = new File(proofDirectory(), "account-round-" + round + ".txt");
        try (FileOutputStream output = new FileOutputStream(account)) {
            output.write((username + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
