package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.FileColorManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import dev.yendrrek.staginggroups.StagingGroupsBundle
import dev.yendrrek.staginggroups.actions.NameValidator
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.domain.StagingGroup
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * An editable copy of every staging group, as a table with a toolbar to add, remove, rename and
 * reorder them. The colour column shows only while [showColours] is on. Nothing reaches the store
 * until the owner applies [groups].
 */
internal class StagingGroupsTable(project: Project) {

    /** One group as edited: mutable, unlike [StagingGroup], so a cell editor can change it in place. */
    internal class Row(val id: String, var name: String, var colourId: String)

    private val colours = FileColorManager.getInstance(project)
    private val palette: List<String> = StagingGroupPalette.colourIds(project)

    private val nameColumn = object : ColumnInfo<Row, String>(StagingGroupsBundle.message("settings.groups.column.name")) {
        private val renderer = NameCell()

        override fun valueOf(item: Row): String = item.name

        override fun getRenderer(item: Row): TableCellRenderer = renderer
    }

    private val colourColumn = object : ColumnInfo<Row, String>(StagingGroupsBundle.message("settings.groups.column.colour")) {
        private val renderer = ColourCell()

        override fun valueOf(item: Row): String = item.colourId

        override fun isCellEditable(item: Row): Boolean = true

        override fun setValue(item: Row, value: String) {
            item.colourId = value
        }

        override fun getRenderer(item: Row): TableCellRenderer = renderer

        override fun getEditor(item: Row): TableCellEditor = ColourCell().withClickCount(1)

        /** Fixed: wide enough for the title and the widest colour, whenever the column appears. */
        override fun getWidth(table: JTable): Int {
            val column = table.columnCount - 1
            val title = table.tableHeader?.defaultRenderer
                ?.getTableCellRendererComponent(table, name, false, false, -1, column)?.preferredSize?.width ?: 0
            val widest = palette.maxOfOrNull { renderer.getTableCellRendererComponent(table, it, false, false, 0, column).preferredSize.width } ?: 0
            return maxOf(title, widest)
        }
    }

    private val model = ListTableModel<Row>(nameColumn)

    internal val table = TableView(model).apply {
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        // No grid lines, like the platform's own settings tables.
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
        emptyText.text = StagingGroupsBundle.message("settings.groups.empty")
    }

    val component: JComponent = ToolbarDecorator.createDecorator(table as JTable)
        .setAddAction { addGroup() }
        .setEditAction { renameSelectedGroup() }
        .setEditActionName(StagingGroupsBundle.message("settings.groups.rename"))
        .createPanel()

    init {
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val column = table.columnAtPoint(event.point)
                if (column < 0 || model.columnInfos[table.convertColumnIndexToModel(column)] !== nameColumn) return false
                renameSelectedGroup()
                return true
            }
        }.installOn(table)
    }

    /** The groups as edited, in display order. */
    fun groups(): List<StagingGroup> =
        model.items.mapIndexed { index, row -> StagingGroup(row.id, row.name, row.colourId, index) }

    fun reset(groups: List<StagingGroup>) {
        table.stopEditing()
        model.items = groups.mapTo(ArrayList()) { Row(it.id, it.name, it.colourId) }
    }

    fun showColours(show: Boolean) {
        val columns: Array<ColumnInfo<*, *>> = if (show) arrayOf(nameColumn, colourColumn) else arrayOf(nameColumn)
        if (model.columnInfos.contentEquals(columns)) return
        table.stopEditing()
        model.columnInfos = columns
        table.updateColumnSizes()
    }

    /** Asks for a name, then appends a group with the next unused colour and selects it. */
    internal fun addGroup() {
        val name = Messages.showInputDialog(
            table,
            StagingGroupsBundle.message("dialog.group.name"),
            StagingGroupsBundle.message("dialog.new.group.title"),
            null,
            "",
            NameValidator { edited() },
        ) ?: return
        val row = Row(UUID.randomUUID().toString(), name.trim(), GroupsCommands.nextColourId(edited(), palette))
        model.addRow(row)
        select(row)
    }

    /** Asks for a new name for the first selected group. */
    internal fun renameSelectedGroup() {
        val row = table.selectedObject ?: return
        val name = Messages.showInputDialog(
            table,
            StagingGroupsBundle.message("dialog.group.name"),
            StagingGroupsBundle.message("dialog.rename.group.title"),
            null,
            row.name,
            NameValidator(row.id) { edited() },
        ) ?: return
        row.name = name.trim()
        val index = model.indexOf(row)
        model.fireTableRowsUpdated(index, index)
    }

    private fun edited(): GroupsState = GroupsState(groups = groups())

    private fun select(row: Row) {
        table.setSelection(listOf(row))
        table.scrollRectToVisible(table.getCellRect(model.indexOf(row), 0, true))
    }

    /** A name cell, its text lined up with the column header's. */
    private class NameCell : DefaultTableCellRenderer() {

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
        ): Component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).also {
            alignWithHeader(this, table, column)
        }
    }

    /** A colour cell: the dot and the colour's name, choosing from the File Colors palette. */
    private inner class ColourCell : ComboBoxTableRenderer<String>(palette.toTypedArray()) {

        override fun getTextFor(value: String): String = colours.getColorName(value)

        override fun getIconFor(value: String): Icon? = StagingGroupIcons.colourDot(colours.getColor(value))

        override fun customizeComponent(value: String, table: JTable, isSelected: Boolean) {
            super.customizeComponent(value, table, isSelected)
            alignWithHeader(this, table, table.columnCount - 1)
        }
    }

    private companion object {
        /**
         * Pads [cell] as the header pads its title, so a column's values start where its title does.
         * Read from the header each time, as the padding comes from the current look and feel.
         */
        fun alignWithHeader(cell: JComponent, table: JTable, column: Int) {
            val header = table.tableHeader ?: return
            val title = header.defaultRenderer.getTableCellRendererComponent(table, "", false, false, -1, column) as? JComponent ?: return
            val padding = title.insets
            cell.border = EmptyBorder(cell.insets.top, padding.left, cell.insets.bottom, padding.right)
        }
    }
}
