package ch.atdit.schulnetzclient;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class CellRenderer implements TableCellRenderer {

    private static final TableCellRenderer RENDERER = new DefaultTableCellRenderer();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component component = RENDERER.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        Color color = null;

        if (Client.getRowsToColor().containsKey(row)) {
            color = new Color(Client.getRowsToColor().get(row));
        }

        component.setBackground(color);

        return component;
    }
}
