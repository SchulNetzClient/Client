package ch.atdit.schulnetzclient;

import javax.swing.*;
import java.awt.*;

class PopUpManager {
    static void createPopUp(String message) {
        final JFrame frame = new JFrame(Reference.VERSION);
        frame.setSize(240,180);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0f;
        constraints.weighty = 1.0f;
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridx++;
        constraints.weightx = 0f;
        constraints.weighty = 0f;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.gridx = 0;
        JTextArea jTextArea = new JTextArea(message);
        jTextArea.setBackground(new Color(-986896));
        jTextArea.setEditable(false);
        Font notenTextFont = getThisFont("Segoe UI", Font.PLAIN, 12, jTextArea.getFont());
        if (notenTextFont != null) jTextArea.setFont(notenTextFont);
        frame.add(jTextArea, constraints);
        constraints.anchor = GridBagConstraints.SOUTH;
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
        frame.setAlwaysOnTop(true);
    }

    @SuppressWarnings({"SameParameterValue", "Duplicates"})
    private static Font getThisFont (String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        return new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    }
}
