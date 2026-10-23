package vn.edu.ueh.thudinh.fashion2hand;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Small value-only drafts; never serializes whole Views or image bitmaps. */
public final class UiFormState {
    private UiFormState() { }
    public static Bundle capture(View root) {
        Bundle draft = new Bundle(); visit(root, "root", draft, false); return draft;
    }
    public static void restore(View root, Bundle draft) {
        if (draft != null) visit(root, "root", draft, true);
    }
    private static void visit(View view, String path, Bundle draft, boolean restore) {
        String key = view.getId() == View.NO_ID ? path : "id:" + view.getId();
        if (view.getTag() instanceof String && ((String) view.getTag()).startsWith("draft:")) key = (String) view.getTag();
        if (view instanceof EditText) {
            EditText input = (EditText) view;
            int variation = input.getInputType() & InputType.TYPE_MASK_VARIATION;
            boolean secret = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            if (!secret) {
                if (restore && draft.containsKey(key)) {
                    input.setText(draft.getString(key, ""));
                    input.setSelection(Math.min(input.length(), draft.getInt(key + ":cursor", input.length())));
                } else if (!restore) {
                    draft.putString(key, input.getText().toString());
                    draft.putInt(key + ":cursor", Math.max(0, input.getSelectionStart()));
                }
            }
        } else if (view instanceof CompoundButton) {
            if (restore && draft.containsKey(key)) ((CompoundButton) view).setChecked(draft.getBoolean(key));
            else if (!restore) draft.putBoolean(key, ((CompoundButton) view).isChecked());
        } else if (view instanceof Spinner) {
            Spinner spinner = (Spinner) view;
            if (restore && draft.containsKey(key) && spinner.getAdapter() != null)
                spinner.setSelection(Math.max(0, Math.min(spinner.getCount() - 1, draft.getInt(key))));
            else if (!restore) draft.putInt(key, spinner.getSelectedItemPosition());
        } else if (view instanceof ScrollView) {
            if (restore && draft.containsKey(key)) { final int scrollY = draft.getInt(key); view.post(() -> view.scrollTo(0, scrollY)); }
            else if (!restore) draft.putInt(key, view.getScrollY());
        }
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) visit(parent.getChildAt(i), path + "/" + i, draft, restore);
        }
    }
}
