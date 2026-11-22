package dev.lyriscope.core;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Unified KernelSU-Style-UI-Kit Miuix OverlayDialog builders: 28dp rounded floating sheet
 * anchored near the bottom on portrait screens and centered on landscape/wide windows.
 */
final class MiuixDialogs {
    interface IndexListener {
        void onSelected(int index);
    }

    private MiuixDialogs() {}

    static Dialog showCustom(
            Context context,
            SettingsKit kit,
            String title,
            String subtitle,
            View bodyView,
            String cancelLabel,
            String confirmLabel,
            boolean danger,
            Runnable onConfirm
    ) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = kit.vertical();
        content.setPadding(kit.dp(24), kit.dp(24), kit.dp(24), kit.dp(22));
        content.setBackground(kit.rect(kit.p.card, 28f));

        if (title != null && !title.trim().isEmpty()) {
            TextView heading = kit.text(title, 19f, kit.p.text, kit.semi);
            heading.setGravity(Gravity.CENTER);
            heading.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            heading.setLineSpacing(kit.dp(2), 1f);
            content.addView(heading, SettingsKit.matchWrap());
        }
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = kit.text(subtitle, 14f, kit.p.text3, kit.regular);
            sub.setGravity(Gravity.CENTER);
            sub.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            sub.setLineSpacing(kit.dp(3), 1f);
            content.addView(sub, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(8)));
        }
        if (bodyView != null) {
            content.addView(bodyView, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(16)));
        }

        if (cancelLabel != null || confirmLabel != null) {
            LinearLayout actions = kit.horizontal();
            if (cancelLabel != null) {
                Button cancel = kit.button(cancelLabel, SettingsKit.BUTTON_NORMAL, null);
                cancel.setOnClickListener(view -> dialog.dismiss());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, kit.dp(48), 1f);
                if (confirmLabel != null) params.setMarginEnd(kit.dp(12));
                actions.addView(cancel, params);
            }
            if (confirmLabel != null) {
                Button confirm = kit.button(confirmLabel,
                        danger ? SettingsKit.BUTTON_DANGER : SettingsKit.BUTTON_PRIMARY, null);
                confirm.setOnClickListener(view -> {
                    dialog.dismiss();
                    if (onConfirm != null) onConfirm.run();
                });
                actions.addView(confirm, new LinearLayout.LayoutParams(0, kit.dp(48), 1f));
            }
            content.addView(actions, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(20)));
        }

        dialog.setContentView(content);
        dialog.show();
        styleWindow(context, dialog, kit.p.dark, 396);
        return dialog;
    }

    static Dialog showSingleChoice(
            Context context,
            SettingsKit kit,
            String title,
            CharSequence[] items,
            int selectedIndex,
            String cancelLabel,
            IndexListener listener
    ) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = kit.vertical();
        content.setPadding(kit.dp(20), kit.dp(24), kit.dp(20), kit.dp(20));
        content.setBackground(kit.rect(kit.p.card, 28f));

        if (title != null && !title.trim().isEmpty()) {
            TextView heading = kit.text(title, 19f, kit.p.text, kit.semi);
            heading.setGravity(Gravity.CENTER);
            heading.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            heading.setLineSpacing(kit.dp(2), 1f);
            content.addView(heading, SettingsKit.matchWrap());
        }

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = kit.vertical();
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int index = 0; index < items.length; index++) {
            final int position = index;
            boolean active = index == selectedIndex;
            TextView row = kit.text(items[index], 15.5f, active ? kit.p.accentText : kit.p.text,
                    active ? kit.semi : kit.regular);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(kit.dp(48));
            row.setPaddingRelative(kit.dp(16), kit.dp(12), kit.dp(16), kit.dp(12));
            row.setBackground(kit.pressable(kit.rect(active ? kit.p.accentSoft : 0x00000000, 14f), 14f));
            if (active) {
                row.setCompoundDrawablePadding(kit.dp(10));
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                        kit.icon("check", kit.p.accentText, 18f), null);
            }
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                if (listener != null) listener.onSelected(position);
            });
            LinearLayout.LayoutParams params = SettingsKit.matchWrap();
            if (index > 0) params.topMargin = kit.dp(4);
            list.addView(row, params);
        }

        int maxListHeight = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.48f);
        int estimatedHeight = items.length * kit.dp(54);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                estimatedHeight > maxListHeight ? maxListHeight : ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollParams.topMargin = kit.dp(16);
        content.addView(scroll, scrollParams);

        if (cancelLabel != null) {
            Button cancel = kit.button(cancelLabel, SettingsKit.BUTTON_NORMAL, null);
            cancel.setOnClickListener(view -> dialog.dismiss());
            content.addView(cancel, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(16)));
        }

        dialog.setContentView(content);
        dialog.show();
        styleWindow(context, dialog, kit.p.dark, 396);
        return dialog;
    }

    static void styleWindow(Context context, Dialog dialog, boolean dark, int maxWidthDp) {
        Window window = dialog.getWindow();
        if (window == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = dark ? 0.6f : 0.35f;
        boolean portrait = context.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        attributes.gravity = portrait ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) : Gravity.CENTER;
        attributes.y = portrait ? Math.round(14f * density) : 0;
        window.setAttributes(attributes);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(Math.round(maxWidthDp * density), Math.max(1, screenWidth - Math.round(24f * density)));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
