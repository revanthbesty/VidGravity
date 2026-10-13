import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WebLibraryBulkActionsTests(unittest.TestCase):
    def test_library_cards_render_selection_checkbox(self):
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        self.assertIn('class="mediaSelect" type="checkbox"', app)
        self.assertIn('data-id="${esc(m.id)}"', app)

    def test_select_visible_and_copy_filename_controls_exist(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        self.assertIn('id="selectVisibleMedia"', html)
        self.assertIn('id="copySelectedNames"', html)
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        self.assertIn("selectVisibleMedia(e.target.checked)", app)
        self.assertIn("copySelectedNames", app)
        self.assertIn("Copied ${names.length} filename(s)", app)

    def test_bulk_actions_are_scoped_to_visible_library_cards(self):
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        self.assertIn("document.querySelectorAll('#libraryGrid .mediaSelect')", app)
        self.assertIn("document.querySelectorAll('#libraryGrid .mediaSelect:checked')", app)
        self.assertIn("if(!confirm(`Delete ${ids.length} selected item(s)?`))return", app)

    def test_selected_filenames_are_joined_by_newlines(self):
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        self.assertIn(r"const text=names.join('\n');", app)


if __name__ == "__main__":
    unittest.main()
