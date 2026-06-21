# -*- coding: utf-8 -*-
"""
Przycinanie marginesow Spiewnik.pdf (bezstratnie, przez CropBox per strona).

Regula:
 - tylko strony nutowe (z piesni.json); pozostale strony nietkniete
 - gora: bez cropu
 - boki: realny atrament + padding 8pt, podloga szerokosci 376pt
 - dol:
     * strona nalezy do jakiejs piesni 2-stronicowej -> usun TYLKO stopke (biel zostaje, strona pelna -> rozkladowka spojna)
     * strona tylko 1-stronicowych piesni            -> ciasno do nut (usun biel + stopke)
"""
import fitz, json, numpy as np, shutil, os
from collections import defaultdict

SRC = "app/src/main/assets/Spiewnik.pdf"
BAK = "Spiewnik_original.pdf"   # poza assetami - nie trafi do APK
OUT = "app/src/main/assets/Spiewnik.pdf"

DPI = 150
THRESH = 245
sy = DPI / 72.0
sx = DPI / 72.0
PAD = 8.0
BOT_PAD = 8.0
FLOOR_W = 376.0
GAP_SEP_PT = 6.0          # bialy odstep >= 6pt = separator stopki
FOOTER_CLEAR_PT = 2.0     # ile pt nad stopka tniemy (wersja 3)


def main():
    if not os.path.exists(BAK):
        shutil.copy2(SRC, BAK)
        print("Kopia zapasowa:", BAK)
    else:
        print("Kopia zapasowa juz istnieje:", BAK)

    songs = json.load(open("app/src/main/assets/piesni.json", encoding="utf-8"))
    page2np = defaultdict(list)
    for s in songs:
        pgs = set(s["strony_pdf"])
        for p in pgs:
            page2np[p].append(len(pgs))
    note_pages = set(page2np)
    # strona "spojna" (wersja 3) jesli dotyka jakiejkolwiek piesni 2-stronicowej
    consistent = {p for p in note_pages if any(n > 1 for n in page2np[p])}

    doc = fitz.open(SRC)
    gap_px = int(round(GAP_SEP_PT * sy))
    changed = 0
    problems = []

    for idx in range(doc.page_count):
        jp = idx + 1
        if jp not in note_pages:
            continue
        page = doc[idx]
        pr = page.rect
        pix = page.get_pixmap(matrix=fitz.Matrix(DPI / 72.0, DPI / 72.0),
                              colorspace=fitz.csGRAY, alpha=False, clip=pr)
        arr = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width)
        mask = arr < THRESH
        rows = mask.any(axis=1)
        cols = mask.any(axis=0)
        if not rows.any():
            continue

        # --- boki ---
        ci = np.where(cols)[0]
        x0 = max(pr.x0, pr.x0 + ci[0] / sx - PAD)
        x1 = min(pr.x1, pr.x0 + (ci[-1] + 1) / sx + PAD)
        y0 = pr.y0  # gora bez cropu

        # --- detekcja stopki / dolu nut ---
        ink = np.where(rows)[0]
        i = ink[-1]
        top = i
        white = 0
        while i > 0:
            if rows[i]:
                top = i
                white = 0
            else:
                white += 1
                if white >= gap_px:
                    break
            i -= 1
        footer_top = top
        j = footer_top - 1
        mb = None
        while j > 0:
            if rows[j]:
                mb = j
                break
            j -= 1

        if mb is None:
            # brak wyraznej stopki - bezpieczny fallback: caly atrament + padding
            ri = np.where(rows)[0]
            y1 = pr.y0 + (ri[-1] + 1) / sy + PAD
        elif jp in consistent:
            # wersja 3: usun tylko stopke, zostaw biel
            y1 = pr.y0 + footer_top / sy - FOOTER_CLEAR_PT
        else:
            # wersja 4: ciasno do nut
            gap = (footer_top - mb) / sy
            y1 = pr.y0 + mb / sy + min(BOT_PAD, gap * 0.6)
        y1 = min(pr.y1, y1)

        # --- podloga szerokosci ---
        if x1 - x0 < FLOOR_W:
            cx = (x0 + x1) / 2.0
            x0 = cx - FLOOR_W / 2.0
            x1 = cx + FLOOR_W / 2.0
            if x0 < pr.x0:
                x1 += pr.x0 - x0
                x0 = pr.x0
            if x1 > pr.x1:
                x0 -= x1 - pr.x1
                x1 = pr.x1

        crop = fitz.Rect(x0, y0, x1, y1) & pr
        if crop.width < 50 or crop.height < 50:
            problems.append((jp, tuple(round(v, 1) for v in crop)))
            continue
        page.set_cropbox(crop)
        changed += 1

    print("Przyciete strony:", changed, "(z", len(note_pages), "nutowych,", doc.page_count, "wszystkich)")
    if problems:
        print("PROBLEMY (pominiete):", problems[:20])
    tmp = OUT + ".tmp"
    doc.save(tmp, garbage=4, deflate=True)
    doc.close()
    os.replace(tmp, OUT)
    print("Zapisano:", OUT)
    v = fitz.open(OUT)
    print("Strony po zapisie:", v.page_count)
    diff = sum(1 for k in range(v.page_count)
               if (round(v[k].rect.width, 1), round(v[k].rect.height, 1)) != (467.7, 666.2))
    print("Strony z innym rozmiarem niz oryginal:", diff)


if __name__ == "__main__":
    main()
