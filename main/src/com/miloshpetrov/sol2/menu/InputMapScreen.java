package com.miloshpetrov.sol2.menu;


import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.miloshpetrov.sol2.Const;
import com.miloshpetrov.sol2.GameOptions;
import com.miloshpetrov.sol2.SolApplication;
import com.miloshpetrov.sol2.common.SolColor;
import com.miloshpetrov.sol2.ui.*;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Config Screen to Change Input Mapping</h1>
 * The input mapping screen is based on the inventory screen used within the game.
 */
public class InputMapScreen implements SolUiScreen {
    public final InputMapKeyboardScreen inputMapKeyboardScreen;
    public final InputMapControllerScreen inputMapControllerScreen;
    public final InputMapMixedScreen inputMapMixedScreen;

    private final List<SolUiControl> controls;
    private final SolUiControl myPrevCtrl;
    private final SolUiControl nextCtrl;
    private final Rectangle myListArea;
    private final SolUiControl[] itemCtrls;
    private final Rectangle myDetailArea;
    private final Rectangle myItemCtrlArea;
    private final Vector2 myDetailHeaderPos;
    private final SolUiControl cancelCtrl;
    private final SolUiControl saveCtrl;
    private final SolUiControl upCtrl;
    private final SolUiControl downCtrl;

    private static final float IMG_COL_PERC = .1f;
    private static final float EQUI_COL_PERC = .1f;
    private static final float PRICE_COL_PERC = .1f;
    private static final float AMT_COL_PERC = .1f;

    private InputMapOperations myOperations;
    private int myPage;
    private int selectedIndex;
    private final Vector2 myListHeaderPos;
    private static final float SMALL_GAP = .004f;
    private static final float HEADER_TEXT_OFFS = .005f;
    private static final int BTN_ROWS = 4;


    public InputMapScreen(float r, GameOptions gameOptions) {
        controls = new ArrayList<SolUiControl>();

        float contentW = .8f;
        float col0 = r / 2 - contentW / 2;
        float row0 = .2f;
        float row = row0;
        float bgGap = MenuLayout.BG_BORDER;
        float bigGap = SMALL_GAP * 6;
        float headerH = .03f;


        // list header & controls
        myListHeaderPos = new Vector2(col0 + HEADER_TEXT_OFFS, row + HEADER_TEXT_OFFS); // offset hack
        float listCtrlW = contentW * .15f;
        Rectangle nextArea = new Rectangle(col0 + contentW - listCtrlW, row, listCtrlW, headerH);
        nextCtrl = new SolUiControl(nextArea, true, gameOptions.getKeyRight());
        nextCtrl.setDisplayName(">");
        controls.add(nextCtrl);
        Rectangle prevArea = new Rectangle(nextArea.x - SMALL_GAP - listCtrlW, row, listCtrlW, headerH);
        myPrevCtrl = new SolUiControl(prevArea, true, gameOptions.getKeyLeft());
        myPrevCtrl.setDisplayName("<");
        controls.add(myPrevCtrl);
        row += headerH + SMALL_GAP;

        // list
        float itemRowH = .04f;
        float listRow0 = row;
        itemCtrls = new SolUiControl[Const.ITEM_GROUPS_PER_PAGE];
        for (int i = 0; i < Const.ITEM_GROUPS_PER_PAGE; i++) {
            Rectangle itemR = new Rectangle(col0, row, contentW, itemRowH);
            SolUiControl itemCtrl = new SolUiControl(itemR, true);
            itemCtrls[i] = itemCtrl;
            controls.add(itemCtrl);
            row += itemRowH + SMALL_GAP;
        }
        myListArea = new Rectangle(col0, row, contentW, row - SMALL_GAP - listRow0);
        row += bigGap;

        // detail header & area
        myDetailHeaderPos = new Vector2(col0 + HEADER_TEXT_OFFS, row + HEADER_TEXT_OFFS); // offset hack
        row += headerH + SMALL_GAP;
        float itemCtrlAreaW = contentW * .4f;
        myItemCtrlArea = new Rectangle(col0 + contentW - itemCtrlAreaW, row, itemCtrlAreaW, .2f);
        myDetailArea = new Rectangle(col0, row, contentW - itemCtrlAreaW - SMALL_GAP, myItemCtrlArea.height);
        row += myDetailArea.height;

        // Add the buttons and controls
        cancelCtrl = new SolUiControl(itemCtrl(3), true, gameOptions.getKeyClose());
        cancelCtrl.setDisplayName("Cancel");
        controls.add(cancelCtrl);

        saveCtrl = new SolUiControl(itemCtrl(2), true);
        saveCtrl.setDisplayName("Save");
        controls.add(saveCtrl);

        upCtrl = new SolUiControl(null, true, gameOptions.getKeyUp());
        controls.add(upCtrl);
        downCtrl = new SolUiControl(null, true, gameOptions.getKeyDown());
        controls.add(downCtrl);

        // Create the input screens
        inputMapKeyboardScreen = new InputMapKeyboardScreen(this, gameOptions);
        inputMapControllerScreen = new InputMapControllerScreen(this, gameOptions);
        inputMapMixedScreen = new InputMapMixedScreen(this, gameOptions);
    }

    @Override
    public List<SolUiControl> getControls() {
        return controls;
    }

    @Override
    public void updateCustom(SolApplication cmp, SolInputManager.Ptr[] ptrs, boolean clickedOutside) {
        GameOptions gameOptions = cmp.getOptions();
        SolInputManager im = cmp.getInputMan();
        MenuScreens screens = cmp.getMenuScreens();

        // Save - saves new settings and returns to the options screen
        if (saveCtrl.isJustOff()) {
            myOperations.save(gameOptions);
            im.setScreen(cmp, screens.options);
        }

        // Cancel - return to options screen without saving
        if (cancelCtrl.isJustOff()) {
            im.setScreen(cmp, screens.options);
        }

        // Disable handling of key inputs while entering a new input key
        if (myOperations.isEnterNewKey()) {
            myPrevCtrl.setEnabled(false);
            nextCtrl.setEnabled(false);
            return;
        }

        // Selected Item Control
        List<InputConfigItem> itemsList = myOperations.getItems(gameOptions);
        int groupCount = itemsList.size();
        int pageCount = groupCount / Const.ITEM_GROUPS_PER_PAGE;

        // Select the item the mouse clicked
        int offset = myPage * Const.ITEM_GROUPS_PER_PAGE;
        for (int i = 0; i < itemCtrls.length; i++) {
            SolUiControl itemCtrl = itemCtrls[i];
            if (itemCtrl.isJustOff()) {
                selectedIndex = i + offset;
            }
        }

        // Left and Right Page Control
        if (myPrevCtrl.isJustOff()) myPage--;
        if (nextCtrl.isJustOff()) myPage++;
        if (pageCount == 0 || pageCount * Const.ITEM_GROUPS_PER_PAGE < groupCount) pageCount += 1;
        if (myPage < 0) myPage = 0;
        if (myPage >= pageCount) myPage = pageCount - 1;
        myPrevCtrl.setEnabled(0 < myPage);
        nextCtrl.setEnabled(myPage < pageCount - 1);

        // Ensure Selected item is on page
        if (selectedIndex < offset || selectedIndex >= offset + Const.ITEM_GROUPS_PER_PAGE) selectedIndex = offset;

        // Up and Down Control
        if (upCtrl.isJustOff()) {
            selectedIndex--;
            if (selectedIndex < 0) selectedIndex = 0;
            if (selectedIndex < offset) myPage--;
        }
        if (downCtrl.isJustOff()) {
            selectedIndex++;
            if (selectedIndex >= groupCount) selectedIndex = groupCount - 1;
            if (selectedIndex >= offset + Const.ITEM_GROUPS_PER_PAGE) myPage++;
            if (myPage >= pageCount) myPage = pageCount - 1;
        }

        // Inform the input screen which item is selected
        myOperations.setSelectedIndex(selectedIndex);
    }

    @Override
    public void drawBg(UiDrawer uiDrawer, SolApplication cmp) {

    }

    @Override
    public void drawImgs(UiDrawer uiDrawer, SolApplication cmp) {

    }

    @Override
    public void drawText(UiDrawer uiDrawer, SolApplication cmp) {
        GameOptions gameOptions = cmp.getOptions();
        List<InputConfigItem> list = myOperations.getItems(gameOptions);

        float imgColW = myListArea.width * IMG_COL_PERC;
        float equiColW = myListArea.width * EQUI_COL_PERC;
        float priceWidth = myListArea.width * PRICE_COL_PERC;
        float amtWidth = myListArea.width * AMT_COL_PERC;
        float nameWidth = myListArea.width - imgColW - equiColW - priceWidth - amtWidth;

        // Display the input mapping in the grid control
        for (int i = 0; i < itemCtrls.length; i++) {
            int groupIdx = myPage * Const.ITEM_GROUPS_PER_PAGE + i;
            int groupCount = list.size();
            if (groupCount <= groupIdx) continue;
            SolUiControl itemCtrl = itemCtrls[i];
            String displayName = list.get(groupIdx).getDisplayName();
            String inputKey = list.get(groupIdx).getInputKey();
            Rectangle rect = itemCtrl.getScreenArea();
            float rowCenterY = rect.y + rect.height / 2;

            // Draw the name of in the input and the key it is mapped to
            uiDrawer.drawString(displayName, rect.x + equiColW + imgColW + nameWidth/2, rowCenterY, FontSize.WINDOW, true, selectedIndex == groupIdx ? SolColor.W : SolColor.G);
            uiDrawer.drawString(inputKey, rect.x + rect.width - amtWidth - priceWidth/2, rowCenterY, FontSize.WINDOW, true, SolColor.LG);
        }

        // Draw the header title
        uiDrawer.drawString(myOperations.getHeader(), myListHeaderPos.x, myListHeaderPos.y, FontSize.WINDOW, false, SolColor.W);

        // Draw the detail text
        uiDrawer.drawString(myOperations.getDisplayDetail(), myDetailArea.x + .015f, myDetailArea.y + .015f, FontSize.WINDOW, false, SolColor.W);
    }

    @Override
    public boolean reactsToClickOutside() {
        return false;
    }

    @Override
    public boolean isCursorOnBg(SolInputManager.Ptr ptr) {
        return false;
    }

    @Override
    public void onAdd(SolApplication cmp) {
        // Add any extra screen information as required by the input screens. E.g. buttons
        if (myOperations != null) {
            cmp.getInputMan().addScreen(cmp, myOperations);
        }

        myPage = 0;
        selectedIndex = 0;
    }

    @Override
    public void blurCustom(SolApplication cmp) {

    }

    public Rectangle itemCtrl(int row) {
        float h = (myItemCtrlArea.height - SMALL_GAP * (BTN_ROWS - 1)) / BTN_ROWS;
        return new Rectangle(myItemCtrlArea.x, myItemCtrlArea.y + (h + SMALL_GAP) * row, myItemCtrlArea.width, h);
    }

    public void setOperations(InputMapOperations operations) {
        myOperations = operations;
    }

}
