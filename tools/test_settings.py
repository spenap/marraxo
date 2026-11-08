import pytest

import settings


def test_the_environment_wins(tmp_path, monkeypatch):
    (tmp_path / "local.env").write_text("MAKOFI_MUSIC=/from/file\n")
    monkeypatch.setenv("MAKOFI_MUSIC", "/from/env")
    assert str(settings.music(tmp_path / "local.env")) == "/from/env"


def test_read_from_the_local_file(tmp_path, monkeypatch):
    monkeypatch.delenv("MAKOFI_MUSIC", raising=False)
    (tmp_path / "local.env").write_text("# my collection\nMAKOFI_MUSIC = /media/music \n")
    assert str(settings.music(tmp_path / "local.env")) == "/media/music"


def test_unset_says_how_to_set_it(tmp_path, monkeypatch):
    monkeypatch.delenv("MAKOFI_MUSIC", raising=False)
    with pytest.raises(SystemExit, match="MAKOFI_MUSIC"):
        settings.music(tmp_path / "local.env")
