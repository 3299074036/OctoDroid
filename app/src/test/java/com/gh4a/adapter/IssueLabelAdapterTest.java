package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Parcel;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Label;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class IssueLabelAdapterTest {
    private Context context;
    private IssueLabelAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new IssueLabelAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private IssueLabelAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Label label(String name, String color) {
        return Label.builder().name(name).color(color).build();
    }

    @Test
    public void editableLabel_new_defaults() {
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel("ff0000");
        assertTrue(l.newlyAdded);
        assertTrue(l.isEditing);
        assertEquals("ff0000", l.editedColor);
        assertNull(l.name());
        assertEquals("ff0000", l.color());
        assertNull(l.base());
        assertEquals(0, l.describeContents());
    }

    @Test
    public void editableLabel_fromLabel_defaults() {
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        assertFalse(l.newlyAdded);
        assertFalse(l.isEditing);
        assertEquals("bug", l.name());
        assertEquals("ff0000", l.color());
        assertTrue(l.base() != null);
    }

    @Test
    public void editableLabel_editedOverridesBase() {
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        l.editedName = "enhancement";
        l.editedColor = "00ff00";
        assertEquals("enhancement", l.name());
        assertEquals("00ff00", l.color());
        l.restoreOriginalProperties();
        assertEquals("bug", l.name());
        assertEquals("ff0000", l.color());
    }

    @Test
    public void editableLabel_nullBaseLabel_nameAndColorNull() {
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel((Label) null);
        assertNull(l.name());
        assertNull(l.color());
    }

    @Test
    public void editableLabel_parcelRoundTrip() {
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        l.editedName = "new name";
        l.editedColor = "123456";
        l.isEditing = true;
        Parcel parcel = Parcel.obtain();
        l.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        IssueLabelAdapter.EditableLabel restored =
                IssueLabelAdapter.EditableLabel.CREATOR.createFromParcel(parcel);
        assertEquals("new name", restored.editedName);
        assertEquals("123456", restored.editedColor);
        assertFalse(restored.newlyAdded);
        assertTrue(restored.isEditing);
        assertEquals("bug", restored.base().name());
        assertEquals(1, IssueLabelAdapter.EditableLabel.CREATOR.newArray(1).length);
        parcel.recycle();
    }

    @Test
    public void editableLabel_parcelRoundTrip_newLabel() {
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel("abcdef");
        Parcel parcel = Parcel.obtain();
        l.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        IssueLabelAdapter.EditableLabel restored =
                IssueLabelAdapter.EditableLabel.CREATOR.createFromParcel(parcel);
        assertTrue(restored.newlyAdded);
        assertEquals("abcdef", restored.editedColor);
        assertNull(restored.base());
        parcel.recycle();
    }

    @Test
    public void bind_existingCollapsed_showsLabelAndColor() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        adapter.onBindViewHolder(h, l);
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.collapsed).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.expanded).getVisibility());
        assertEquals("bug", ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
        assertEquals("ff0000", l.editedColor);
    }

    @Test
    public void bind_newLabel_editing_showsEditorWithHint() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel("00ff00");
        adapter.onBindViewHolder(h, l);
        assertEquals(View.GONE, h.itemView.findViewById(R.id.collapsed).getVisibility());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.expanded).getVisibility());
        assertEquals(context.getString(R.string.issue_label_new),
                ((EditText) h.itemView.findViewById(R.id.et_label)).getHint().toString());
    }

    @Test
    public void bind_nullColor_fallsBackToGray() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", null));
        adapter.onBindViewHolder(h, l);
        assertEquals("999999", l.editedColor);
        assertEquals("bug", ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void bind_editedName_shownInEditor() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        l.editedName = "renamed";
        adapter.onBindViewHolder(h, l);
        assertEquals("renamed",
                ((EditText) h.itemView.findViewById(R.id.et_label)).getText().toString());
    }

    @Test
    public void editor_typing_updatesEditedName() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        adapter.onBindViewHolder(h, l);
        EditText editor = h.itemView.findViewById(R.id.et_label);
        editor.setText("typed");
        assertEquals("typed", l.editedName);
    }

    @Test
    public void onClick_colorSwatch_assignsColor() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        adapter.onBindViewHolder(h, l);
        ViewGroup colors = h.itemView.findViewById(R.id.colors);
        View swatch = colors.getChildAt(0);
        assertEquals("dddddd", swatch.getTag());
        adapter.onClick(swatch);
        assertEquals("dddddd", l.editedColor);
    }

    @Test
    public void onClick_customButton_showsColorPickerDialog() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        adapter.onBindViewHolder(h, l);
        View custom = h.itemView.findViewById(R.id.custom);
        adapter.onClick(custom);
        // dialog shown without crash; it is the latest shown AlertDialog
        assertTrue(org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().size() >= 1);
    }

    @Test
    public void onClick_nonColorView_delegatesToSuper() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        adapter.onBindViewHolder(h, l);
        View title = h.itemView.findViewById(R.id.tv_title);
        title.setTag(h);
        adapter.onClick(title); // super.onClick: NO_POSITION -> no-op
    }

    @Test
    public void editableLabel_parcelRoundTrip_notEditing() {
        // isEditing=false + newlyAdded=false: both readInt()==0 sides of
        // the parcel ctor (lines 48/81 in IssueLabelAdapter).
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        Parcel parcel = Parcel.obtain();
        l.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        IssueLabelAdapter.EditableLabel restored =
                IssueLabelAdapter.EditableLabel.CREATOR.createFromParcel(parcel);
        assertFalse(restored.isEditing);
        assertFalse(restored.newlyAdded);
        assertEquals("bug", restored.base().name());
        parcel.recycle();
    }

    @Test
    public void afterTextChanged_withoutBind_noCrash() {
        // holder never bound: lastAssignedLabel is null, the watcher no-ops.
        IssueLabelAdapter.ViewHolder h = holder();
        ((EditText) h.itemView.findViewById(R.id.et_label)).setText("x");
    }

    @Test
    public void onClick_customButton_okButton_appliesPickedColor() {
        IssueLabelAdapter.ViewHolder h = holder();
        IssueLabelAdapter.EditableLabel l =
                new IssueLabelAdapter.EditableLabel(label("bug", "ff0000"));
        adapter.onBindViewHolder(h, l);
        adapter.onClick(h.itemView.findViewById(R.id.custom));
        com.gh4a.ColorPickerDialog dialog = null;
        for (android.app.Dialog d :
                org.robolectric.shadows.ShadowDialog.getShownDialogs()) {
            if (d instanceof com.gh4a.ColorPickerDialog) {
                dialog = (com.gh4a.ColorPickerDialog) d;
            }
        }
        assertTrue(dialog != null);
        // holocolorpicker: ColorPicker.setColor() moves the wheel/bars but
        // getColor() reads mCenterNewColor, which only changes on touch;
        // set the picked color directly to model a user picking green
        com.larswerkman.holocolorpicker.ColorPicker picker =
                (com.larswerkman.holocolorpicker.ColorPicker)
                        dialog.findViewById(R.id.color_picker);
        AdapterTestSupport.setField(picker, "mCenterNewColor", 0xFF00FF00);
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        // the AlertDialog button dispatches via a Handler message
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("00ff00", l.editedColor);
    }
}
