"""
key_hierarchy.py — Heirlooms Key Hierarchy Animation
=====================================================

Animates the HKDF derivation tree from the master key (MK) down to
per-purpose sub-keys. Shows each derived key branching out with its
purpose label and a one-way-function gate.

Requires Manim Community Edition >= 0.18:
    pip install manim

Render commands:
    # Low quality (fast preview):
    manim -pql key_hierarchy.py KeyHierarchy

    # High quality:
    manim -pqh key_hierarchy.py KeyHierarchy

    # Export to GIF:
    manim -pql --format=gif key_hierarchy.py KeyHierarchy

Outputs to: ./media/videos/key_hierarchy/
"""

from manim import (
    Scene, VGroup, Rectangle, Text, Arrow, Line, Dot,
    FadeIn, FadeOut, GrowArrow, Write, Create, AnimationGroup,
    MoveToTarget, Transform, MathTex,
    UP, DOWN, LEFT, RIGHT, ORIGIN,
    WHITE, BLUE, GREEN, YELLOW, ORANGE, PURPLE, GREY, BLACK,
    BLUE_D, BLUE_A, GREEN_D, YELLOW_D, ORANGE_D, PURPLE_D,
    config, DEFAULT_FONT_SIZE,
)
import numpy as np

# --- Colour palette ---
COLOR_MK = BLUE_D          # Master key
COLOR_HKDF = ORANGE        # HKDF gate
COLOR_SK = GREEN_D         # Sub-key / derived key
COLOR_DEK = YELLOW_D       # DEK (Data Encryption Key)
COLOR_LABEL = WHITE
COLOR_DIM = GREY


def key_box(label: str, sublabel: str = "", color=WHITE, width=2.8, height=0.65):
    """Returns a VGroup: a rounded rectangle with a label."""
    box = Rectangle(
        width=width,
        height=height,
        color=color,
        fill_color=color,
        fill_opacity=0.15,
        stroke_width=2,
    )
    txt = Text(label, font_size=18, color=color)
    txt.move_to(box.get_center())
    if sublabel:
        sub = Text(sublabel, font_size=13, color=COLOR_DIM)
        sub.next_to(txt, DOWN, buff=0.05)
        grp = VGroup(box, txt, sub)
    else:
        grp = VGroup(box, txt)
    return grp


def hkdf_gate(label: str = "HKDF"):
    """A small diamond/triangle representing the HKDF one-way gate."""
    tri = MathTex(r"\triangledown", color=COLOR_HKDF, font_size=30)
    lbl = Text(label, font_size=12, color=COLOR_HKDF)
    lbl.next_to(tri, RIGHT, buff=0.08)
    return VGroup(tri, lbl)


class KeyHierarchy(Scene):
    def construct(self):
        # ------------------------------------------------------------------ #
        # Title
        # ------------------------------------------------------------------ #
        title = Text(
            "Heirlooms Key Hierarchy", font_size=30, color=WHITE
        )
        title.to_edge(UP, buff=0.3)
        subtitle = Text(
            "MK → HKDF sub-keys", font_size=18, color=COLOR_DIM
        )
        subtitle.next_to(title, DOWN, buff=0.1)
        self.play(Write(title), FadeIn(subtitle, shift=UP * 0.2))
        self.wait(0.5)

        # ------------------------------------------------------------------ #
        # Master Key (MK) — top node
        # ------------------------------------------------------------------ #
        mk_box = key_box("MK", "Master Key  (256-bit)", color=BLUE_D, width=3.5)
        mk_box.move_to(UP * 2.2)
        mk_label = Text(
            "Argon2id(passphrase, salt)  /  CryptoKit (iOS)",
            font_size=12,
            color=COLOR_DIM,
        )
        mk_label.next_to(mk_box, RIGHT, buff=0.3)

        self.play(FadeIn(mk_box, shift=DOWN * 0.3), FadeIn(mk_label))
        self.wait(0.4)

        # ------------------------------------------------------------------ #
        # HKDF trunk arrow
        # ------------------------------------------------------------------ #
        trunk_arrow = Arrow(
            mk_box.get_bottom(),
            mk_box.get_bottom() + DOWN * 0.6,
            color=COLOR_HKDF,
            buff=0.05,
            stroke_width=3,
        )
        hkdf_label = Text("HKDF-SHA256", font_size=14, color=COLOR_HKDF)
        hkdf_label.next_to(trunk_arrow, RIGHT, buff=0.15)
        self.play(GrowArrow(trunk_arrow), FadeIn(hkdf_label))

        # ------------------------------------------------------------------ #
        # Sub-keys — fan out below
        # Derived purposes from NOT-001 §2
        # ------------------------------------------------------------------ #
        sub_keys = [
            ("K_tag", "tag-token-v1", "Tag token key", GREEN_D),
            ("K_disp", "tag-display-v1", "Tag display key", GREEN_D),
            ("K_auto", "auto-tag-token-v1", "Auto-tag key", PURPLE_D),
        ]

        # Horizontal spread positions
        n = len(sub_keys)
        x_spread = 4.0
        x_positions = np.linspace(-x_spread, x_spread, n)
        y_sub = 0.5

        branch_point = mk_box.get_bottom() + DOWN * 0.6

        sub_boxes = []
        arrows = []
        for i, (short, info_str, desc, col) in enumerate(sub_keys):
            pos = np.array([x_positions[i], y_sub, 0])
            box = key_box(short, info_str, color=col, width=2.5, height=0.7)
            box.move_to(pos)

            # Arrow from branch point to box top
            arr = Arrow(
                branch_point,
                box.get_top(),
                color=col,
                buff=0.05,
                stroke_width=2,
            )

            # Usage label below box
            use_label = Text(desc, font_size=12, color=COLOR_DIM)
            use_label.next_to(box, DOWN, buff=0.1)

            sub_boxes.append((box, use_label))
            arrows.append(arr)

        self.play(
            AnimationGroup(
                *[GrowArrow(a) for a in arrows],
                lag_ratio=0.2,
            )
        )
        self.play(
            AnimationGroup(
                *[FadeIn(box, shift=DOWN * 0.2) for box, _ in sub_boxes],
                lag_ratio=0.2,
            )
        )
        self.play(
            AnimationGroup(
                *[FadeIn(use_lbl) for _, use_lbl in sub_boxes],
                lag_ratio=0.2,
            )
        )
        self.wait(0.4)

        # ------------------------------------------------------------------ #
        # Show HMAC usage from K_tag
        # ------------------------------------------------------------------ #
        k_tag_box = sub_boxes[0][0]
        hmac_note = Text(
            "HMAC-SHA256(K_tag, tag_value) → token T",
            font_size=13,
            color=YELLOW_D,
        )
        hmac_note.next_to(k_tag_box, DOWN, buff=0.55)
        hmac_arr = Arrow(
            k_tag_box.get_bottom(),
            hmac_note.get_top(),
            color=YELLOW_D,
            buff=0.05,
            stroke_width=2,
        )
        self.play(GrowArrow(hmac_arr), FadeIn(hmac_note))
        self.wait(0.5)

        # ------------------------------------------------------------------ #
        # Separate DEK section — show it is NOT derived from MK
        # ------------------------------------------------------------------ #
        dek_note = Text(
            "DEK  ←  random{0,1}²⁵⁶   (per file; not derived from MK)",
            font_size=14,
            color=COLOR_DEK,
        )
        dek_note.to_edge(DOWN, buff=0.5)
        self.play(FadeIn(dek_note, shift=UP * 0.2))
        self.wait(0.5)

        # ------------------------------------------------------------------ #
        # Wrap up
        # ------------------------------------------------------------------ #
        footer = Text(
            "See NOT-001 for full formal notation",
            font_size=12,
            color=COLOR_DIM,
        )
        footer.to_corner(DOWN + RIGHT, buff=0.2)
        self.play(FadeIn(footer))
        self.wait(1.5)

        self.play(
            FadeOut(title), FadeOut(subtitle), FadeOut(mk_box),
            FadeOut(mk_label), FadeOut(trunk_arrow), FadeOut(hkdf_label),
            *[FadeOut(a) for a in arrows],
            *[FadeOut(b) for b, _ in sub_boxes],
            *[FadeOut(u) for _, u in sub_boxes],
            FadeOut(hmac_arr), FadeOut(hmac_note),
            FadeOut(dek_note), FadeOut(footer),
            run_time=1.0,
        )
