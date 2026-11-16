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
            if (!("reopen".equals(mode) || "validation".equals(mode) || "legal".equals(mode) || "poster".equals(mode) || "posterDocument".equals(mode) ||
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
            if ("legal".equals(mode)) {
                reviewFirstLaunchLegal();
                result.putString("stream", "First-launch legal documents regression passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("true".equals(arguments.getString("fresh"))) prepareFirstLaunch();
            if ("poster".equals(mode) || "posterDocument".equals(mode)) {
                reviewPosterStorage("posterDocument".equals(mode));
                result.putString("stream", "Poster storage regression passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            } else if ("reopen".equals(arguments.getString("mode"))) {
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
            StringBuilder detail = new StringBuilder(error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            for (int i = 0; i < Math.min(5, error.getStackTrace().length); i++) detail.append(error.getStackTrace()[i]).append('\n');
            result.putString("stream", detail.toString());
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

    private View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String string(String name) {
        int resource = getTargetContext().getResources().getIdentifier(name, "string", packageName);
        Activity current = resumedActivity;
        return current == null ? getTargetContext().getString(resource) : current.getString(resource);
    }

    private void clickText(Activity activity, String label) {
        instrumentation.runOnMainSync(() -> {
            View view = findText(activity.getWindow().getDecorView(), label);
            assertNotNull(view);
            while (!view.isClickable() && view.getParent() instanceof View) view = (View) view.getParent();
            view.requestRectangleOnScreen(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()), true);
            assertTrue("Poster control disabled", view.isEnabled());
            assertTrue("Poster control did not respond", view.performClick());
        });
    }

    private android.webkit.WebView findWebView(View view) {
        if (view instanceof android.webkit.WebView) return (android.webkit.WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.webkit.WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void reviewFirstLaunchLegal() throws Exception {
        // onStart runs on the instrumentation thread; let Application.onCreate finish
        // before changing the receipt used by its consent guard.
        instrumentation.waitForIdleSync();
        File receipt = new File(getTargetContext().getNoBackupFilesDir(), "xingdun-privacy-consent");
        byte[] previous = receipt.exists() ? java.nio.file.Files.readAllBytes(receipt.toPath()) : null;
        try {
            assertTrue("Cannot temporarily reset consent for first-launch test", !receipt.exists() || receipt.delete());
            open("io.trtc.tuikit.chat.demo.xingdun.launch.XingDunStartupActivity");
            for (String key : new String[]{"xingdun_user_agreement_link", "xingdun_privacy_policy_link"}) {
                String label = string(key);
                boolean clicked = false;
                long deadline = SystemClock.uptimeMillis() + 15000;
                while (!clicked && SystemClock.uptimeMillis() < deadline) {
                    AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                    if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
                        if (label.contentEquals(node.getText()) && node.isClickable()) {
                            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            break;
                        }
                    }
                    if (!clicked) SystemClock.sleep(200);
                }
                assertTrue("First privacy dialog link not found: " + key, clicked);
                android.webkit.WebView[] reader = {null};
                deadline = SystemClock.uptimeMillis() + 15000;
                while (reader[0] == null && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync(() -> {
                        Activity current = resumedActivity;
                        if (current != null) reader[0] = findWebView(current.getWindow().getDecorView());
                    });
                    if (reader[0] == null) SystemClock.sleep(200);
                }
                assertNotNull(reader[0]);
                Activity legal = resumedActivity;
                if ("true".equals(arguments.getString("blockLegalNetwork"))) {
                    instrumentation.runOnMainSync(() -> {
                        reader[0].getSettings().setBlockNetworkLoads(true);
                        reader[0].reload();
                    });
                    SystemClock.sleep(1200);
                }
                String expected = key.contains("agreement") ? "叙音坊用户协议" : "叙音坊隐私政策";
                boolean loaded = false;
                deadline = SystemClock.uptimeMillis() + 18000;
                while (!loaded && SystemClock.uptimeMillis() < deadline) {
                    boolean[] rendered = {false};
                    instrumentation.runOnMainSync(() -> rendered[0] = reader[0].getVisibility() == View.VISIBLE
                            && expected.equals(reader[0].getTitle()) && reader[0].getProgress() == 100
                            && reader[0].getContentHeight() > 500
                            && !findText(legal.getWindow().getDecorView(), string("xingdun_legal_load_failed")).isShown());
                    loaded = rendered[0];
                    if (!loaded) SystemClock.sleep(250);
                }
                capture(key + ("true".equals(arguments.getString("blockLegalNetwork")) ? "-offline" : "-online"));
                assertTrue("Legal document did not render: " + key, loaded);
                assertTrue("Viewing a document must not accept consent", !receipt.exists());
                clickText(legal, string("xingdun_back"));
                SystemClock.sleep(500);
            }
            assertTrue("Returning to privacy dialog must not accept consent", !receipt.exists());
            capture("first-privacy-after-reading");
        } finally {
            if (previous == null) receipt.delete();
            else java.nio.file.Files.write(receipt.toPath(), previous);
        }
    }

    private void assertStorageNotGranted() {
        for (String permission : new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}) {
            assertTrue("Storage permission unexpectedly granted", getTargetContext().checkSelfPermission(permission)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED);
        }
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (root == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(200);
            root = getUiAutomation().getRootInActiveWindow();
        }
        assertTrue("No foreground UI for permission verification", root != null);
        String owner = String.valueOf(root.getPackageName());
        assertTrue("Unexpected runtime permission prompt", !owner.contains("permissioncontroller") && !owner.contains("packageinstaller"));
    }

    private void reviewPosterStorage(boolean documentFlow) throws Exception {
        open("io.trtc.tuikit.chat.demo.xingdun.launch.XingDunStartupActivity");
        awaitHome();
        SystemClock.sleep(1500);
        assertStorageNotGranted();
        Intent intent = new Intent().setClassName(packageName,
                "io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity");
        intent.putExtra("mode", "invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = startActivitySync(intent);
        final Bitmap[] poster = {null};
        long deadline = SystemClock.uptimeMillis() + 45000;
        while (poster[0] == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                View image = findDescription(activity.getWindow().getDecorView(), string("xingdun_invite_poster_description"));
                if (image instanceof android.widget.ImageView) {
                    android.graphics.drawable.Drawable drawable = ((android.widget.ImageView) image).getDrawable();
                    if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                        poster[0] = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                    }
                }
            });
            if (poster[0] == null) SystemClock.sleep(200);
        }
        assertNotNull(poster[0]);
        assertTrue("Poster image is incomplete", poster[0].getWidth() == 1080 && poster[0].getHeight() == 1440);
        SystemClock.sleep(1500);
        assertStorageNotGranted();
        capture("poster-preview");
        clickText(activity, string("xingdun_copy_share_link"));
        assertStorageNotGranted();

        if (!documentFlow) {
            clickText(activity, string("xingdun_save_invite_poster"));
        } else {
            // Exercise the production legacy result callback using the real system picker.
            // SDK selection is checked by unit tests; this does not pretend the phone is API 28.
            java.lang.reflect.Field pending = activity.getClass().getDeclaredField(arguments.getString("pendingPosterField"));
            java.lang.reflect.Field launcherField = activity.getClass().getDeclaredField(arguments.getString("posterDocumentField"));
            pending.setAccessible(true);
            launcherField.setAccessible(true);
            final Throwable[] failure = {null};
            instrumentation.runOnMainSync(() -> {
                try {
                    pending.set(activity, poster[0]);
                    Object launcher = launcherField.get(activity);
                    java.lang.reflect.Method launch = null;
                    for (Class<?> type = launcher.getClass(); type != null && launch == null; type = type.getSuperclass()) {
                        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                            int count = method.getParameterCount();
                            if (method.getReturnType() == Void.TYPE && (count == 1 || count == 2)
                                    && method.getParameterTypes()[0].isAssignableFrom(String.class)) { launch = method; break; }
                        }
                    }
                    assertNotNull(launch);
                    launch.setAccessible(true);
                    String name = arguments.getString("documentName", "vivo_poster_review.png");
                    if (launch.getParameterCount() == 1) launch.invoke(launcher, name);
                    else launch.invoke(launcher, name, null);
                } catch (Throwable error) { failure[0] = error; }
            });
            if (failure[0] != null) throw new AssertionError("Document picker launch failed: " + failure[0], failure[0]);
            System.out.println("POSTER_DOCUMENT_PICKER_READY");
            deadline = SystemClock.uptimeMillis() + 120000;
            while (pending.get(activity) != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(250);
            assertTrue("System picker did not return", pending.get(activity) == null);
            if ("cancel".equals(arguments.getString("documentAction"))) {
                SystemClock.sleep(800);
                clickText(activity, string("xingdun_copy_share_link"));
                assertStorageNotGranted();
                capture("poster-document-cancel");
                return;
            }
        }
        String saved = string(documentFlow ? "xingdun_invite_poster_saved_to_document" : "xingdun_invite_poster_saved");
        final boolean[] success = {false};
        deadline = SystemClock.uptimeMillis() + 30000;
        while (!success[0] && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> success[0] = findText(activity.getWindow().getDecorView(), saved) != null);
            if (!success[0]) SystemClock.sleep(200);
        }
        assertTrue("Poster save did not succeed", success[0]);
        assertStorageNotGranted();
        capture(documentFlow ? "poster-document-saved" : "poster-pictures-saved");
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

    private void awaitEnabled(Activity activity, String name) {
        final boolean[] enabled = {false};
        long deadline = SystemClock.uptimeMillis() + 30000;
        while (!enabled[0] && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> enabled[0] = activity.findViewById(id(name)).isEnabled());
            if (!enabled[0]) SystemClock.sleep(200);
        }
        assertTrue("Action disabled: " + name, enabled[0]);
    }

    private void click(Activity activity, String name) {
        awaitEnabled(activity, name);
        final boolean[] clicked = {false};
        instrumentation.runOnMainSync(() -> clicked[0] = activity.findViewById(id(name)).performClick());
        assertTrue("Action did not handle click: " + name, clicked[0]);
    }

    private void prepareLoginForm(Activity activity) throws Exception {
        awaitEnabled(activity, "xingdun_primary_action");
        final boolean[] needsSelection = {false};
        instrumentation.runOnMainSync(() -> {
            View group = activity.findViewById(id("travel_company_group"));
            TextView code = activity.findViewById(id("xingdun_company_code"));
            needsSelection[0] = group.getVisibility() == View.VISIBLE && code.getText().toString().trim().isEmpty();
        });
        if (!needsSelection[0]) return;
        String company = arguments.getString("companyCode", "");
        assertTrue("Visible enterprise selection requires an explicit companyCode argument", !company.isEmpty());
        click(activity, "xingdun_company_code");
        AccessibilityNodeInfo input = null;
        long deadline = SystemClock.uptimeMillis() + 30000;
        while (input == null && SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId(packageName + ":id/travel_company_input");
                if (!matches.isEmpty()) input = matches.get(0);
            }
            if (input == null) SystemClock.sleep(200);
        }
        assertNotNull(input);
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, company);
        assertTrue("Enterprise input did not accept text", input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text));
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        java.util.List<AccessibilityNodeInfo> confirms = root.findAccessibilityNodeInfosByViewId(packageName + ":id/travel_company_confirm");
        assertTrue("Enterprise confirmation is missing", !confirms.isEmpty());
        assertTrue("Enterprise confirmation failed", confirms.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        final boolean[] selected = {false};
        deadline = SystemClock.uptimeMillis() + 30000;
        while (!selected[0] && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> selected[0] = company.equals(((TextView) activity.findViewById(id("xingdun_company_code"))).getText().toString().trim())
                    && activity.findViewById(id("xingdun_primary_action")).isEnabled());
            if (!selected[0]) SystemClock.sleep(200);
        }
        assertTrue("Enterprise selection did not complete through the UI", selected[0]);
        capture("enterprise-selected");
    }

    private Activity logoutFromHome(Activity homeActivity) throws Exception {
        Intent profile = new Intent().setClassName(packageName, "io.trtc.tuikit.chat.demo.main.MainActivity");
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
        return login;
    }

    private Activity authenticationScreen() throws Exception {
        if (resumedActivity == null) prepareFirstLaunch();
        long deadline = SystemClock.uptimeMillis() + 45000;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity current = resumedActivity;
            if (current != null && current.getClass().getName().endsWith("MainActivity")) {
                return logoutFromHome(current);
            }
            if (current != null && current.getClass().getName().endsWith("XingDunLaunchActivity")) {
                final boolean[] ready = {false};
                instrumentation.runOnMainSync(() -> {
                    View action = current.findViewById(id("xingdun_primary_action"));
                    ready[0] = action != null && action.isEnabled();
                });
                if (ready[0]) return current;
            }
            SystemClock.sleep(200);
        }
        throw new AssertionError("Startup did not settle on a ready login form or home");
    }

    private void awaitHome() {
        awaitHome(null);
    }

    private void awaitHome(Activity diagnosticActivity) {
        final String[] feedback = {""};
        long deadline = SystemClock.uptimeMillis() + 45000;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity current = resumedActivity;
            if (current != null && current.getClass().getName().equals("io.trtc.tuikit.chat.demo.main.MainActivity")) return;
            Activity form = current != null ? current : diagnosticActivity;
            if (form != null) instrumentation.runOnMainSync(() -> {
                TextView status = form.findViewById(id("xingdun_launch_status"));
                if (status != null && !status.getText().toString().isEmpty()) {
                    feedback[0] = status.getText().toString();
                }
                TextView snackbar = form.findViewById(id("snackbar_text"));
                if (snackbar != null && snackbar.getVisibility() == View.VISIBLE) {
                    feedback[0] = snackbar.getText().toString();
                }
            });
            SystemClock.sleep(200);
        }
        try { capture("authentication-timeout"); } catch (Exception ignored) {}
        fail("Authentication did not reach the real home activity. Displayed feedback: " + feedback[0]);
    }

    public void numericAccountWithOmittedNicknameRegisters() throws Exception {
        String round = arguments.getString("round", "1");
        String username = "19900" + String.valueOf(System.currentTimeMillis()).substring(7);
        String password = "Review_" + UUID.randomUUID().toString().substring(0, 8) + "!";
        String registrationClass = "io.trtc.tuikit.chat.demo.xingdun.launch.XingDunRegistrationActivity";
        String featureClass = "io.trtc.tuikit.chat.demo.main.MainActivity";
        Activity launch = authenticationScreen();
        prepareLoginForm(launch);
        click(launch, "xingdun_switch_mode");
        Activity openedRegistration = null;
        long registrationDeadline = SystemClock.uptimeMillis() + 30000;
        while (openedRegistration == null && SystemClock.uptimeMillis() < registrationDeadline) {
            Activity current = resumedActivity;
            if (current != null && current.getClass().getName().equals(registrationClass)) openedRegistration = current;
            else SystemClock.sleep(200);
        }
        assertNotNull(openedRegistration);
        Activity registration = openedRegistration;
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
            Activity loginForm = logoutFromHome(opened);
            prepareLoginForm(loginForm);
            text(loginForm, "xingdun_username", username);
            text(loginForm, "xingdun_password", password);
            instrumentation.runOnMainSync(() -> ((CheckBox) loginForm.findViewById(id("xingdun_privacy_consent"))).setChecked(true));
            resumedActivity = null;
            click(loginForm, "xingdun_primary_action");
            awaitHome(loginForm);
            capture("login-after-registration");
        }
        File account = new File(proofDirectory(), "account-round-" + round + ".txt");
        try (FileOutputStream output = new FileOutputStream(account)) {
            output.write((username + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
