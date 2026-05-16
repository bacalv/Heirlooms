"""
capsule_seal.py — Heirlooms Capsule Sealing Ceremony Animation
==============================================================

Animates the M11 capsule sealing ceremony:
  1. DEK appears on screen (capsule content key)
  2. XOR split: DEK → DEK_client (client mask) + DEK_tlock (server component)
  3. DEK_client sealed with IBE time-lock (tlock)
  4. DEK_client and DEK wrapped to recipients via ECDH (shown as padlocks)
  5. DEK_tlock sent to server
  6. Final state: what client knows vs what server holds (server-blindness)

Requires Manim Community Edition >= 0.18:
    pip install manim

Render commands:
    manim -pql capsule_seal.py CapsuleSeal     # Low quality / fast preview
    manim -pqh capsule_seal.py CapsuleSeal     # High quality
    manim -pql --format=gif capsule_seal.py CapsuleSeal

Outputs to: ./media/videos/capsule_seal/
"""

from manim import (
    Scene, VGroup, Rectangle, RoundedRectangle, Text, Arrow, Line,
    Dot, Circle, MathTex, Square,
    FadeIn, FadeOut, GrowArrow, Write, Create, AnimationGroup,
    Transform, ReplacementTransform, Indicate, Flash,
    UP, DOWN, LEFT, RIGHT, ORIGIN, DR, DL, UR, UL,
    WHITE, BLUE, GREEN, YELLOW, ORANGE, RED, PURPLE, GREY, BLACK,
    BLUE_D, BLUE_A, GREEN_D, YELLOW_D, ORANGE_D, PURPLE_D, RED_D,
    BLUE_E, GREEN_E,
    config,
    CurvedArrow, ArcBetweenPoints,
    SurroundingRectangle,
)
import numpy as np

# Colour palette
C_DEK       = GREEN_D       # Full DEK (content key)
C_CLIENT    = BLUE_D        # DEK_client (client mask)
C_TLOCK     = RED_D         # DEK_tlock (server component)
C_TLOCK_IBE = ORANGE        # IBE-sealed DEK_client
C_SERVER    = GREY          # Server region
C_LABEL     = WHITE
C_DIM       = "#888888"
C_GATE      = YELLOW_D      # XOR gate


def key_block(label: str, color=WHITE, width=2.2, height=0.55, opacity=0.2):
    box = RoundedRectangle(
        corner_radius=0.1,
        width=width,
        height=height,
        color=color,
        fill_color=color,
        fill_opacity=opacity,
        stroke_width=2,
    )
    txt = Text(label, font_size=18, color=color, weight="BOLD")
    txt.move_to(box)
    return VGroup(box, txt)


def party_region(label: str, width=5.5, height=5.0, color=WHITE, fill_color="#111111"):
    box = Rectangle(
        width=width,
        height=height,
        color=color,
        fill_color=fill_color,
        fill_opacity=0.08,
        stroke_width=1,
        stroke_opacity=0.5,
    )
    lbl = Text(label, font_size=16, color=color)
    lbl.move_to(box.get_top() + DOWN * 0.25)
    return VGroup(box, lbl)


class CapsuleSeal(Scene):
    def construct(self):

        # ------------------------------------------------------------------ #
        # Layout: Client region (left) | Server region (right)
        # ------------------------------------------------------------------ #
        client_region = party_region("Client (sealing)", width=6.0, height=7.5, color=BLUE)
        server_region = party_region("Server", width=5.5, height=7.5, color=C_SERVER)
        client_region.shift(LEFT * 3.3)
        server_region.shift(RIGHT * 3.5)

        title = Text("Capsule Sealing Ceremony", font_size=28, color=WHITE)
        title.to_edge(UP, buff=0.15)

        self.play(Write(title))
        self.play(FadeIn(client_region), FadeIn(server_region))
        self.wait(0.3)

        # ------------------------------------------------------------------ #
        # Step 1: DEK appears on client
        # ------------------------------------------------------------------ #
        dek = key_block("DEK", color=C_DEK, width=2.0)
        dek.move_to(LEFT * 3.5 + UP * 2.5)
        step1 = Text("① Generate capsule DEK  (random 256-bit)", font_size=14, color=C_DIM)
        step1.to_edge(DOWN, buff=1.8)

        self.play(FadeIn(dek, shift=DOWN * 0.3), Write(step1))
        self.wait(0.6)
        self.play(FadeOut(step1))

        # ------------------------------------------------------------------ #
        # Step 2: XOR split
        # ------------------------------------------------------------------ #
        xor_gate = Circle(radius=0.25, color=C_GATE, stroke_width=2)
        xor_symbol = MathTex(r"\oplus", color=C_GATE, font_size=28)
        xor_symbol.move_to(xor_gate)
        xor_group = VGroup(xor_gate, xor_symbol)
        xor_group.move_to(LEFT * 3.5 + UP * 1.2)

        step2 = Text("② XOR split: DEK_client (mask) + DEK_tlock (server component)", font_size=14, color=C_DIM)
        step2.to_edge(DOWN, buff=1.8)

        arr_to_xor = Arrow(dek.get_bottom(), xor_group.get_top(), color=C_DEK, buff=0.05, stroke_width=2)
        self.play(GrowArrow(arr_to_xor), FadeIn(xor_group), Write(step2))

        dek_client = key_block("DEK_client", color=C_CLIENT, width=2.2)
        dek_client.move_to(LEFT * 5.2 + DOWN * 0.3)
        dek_tlock_c = key_block("DEK_tlock", color=C_TLOCK, width=2.2)
        dek_tlock_c.move_to(LEFT * 1.9 + DOWN * 0.3)

        arr_client = Arrow(xor_group.get_bottom(), dek_client.get_top() + RIGHT * 0.3, color=C_CLIENT, buff=0.05, stroke_width=2)
        arr_tlock_c = Arrow(xor_group.get_bottom(), dek_tlock_c.get_top() + LEFT * 0.3, color=C_TLOCK, buff=0.05, stroke_width=2)

        self.play(
            GrowArrow(arr_client), FadeIn(dek_client, shift=DOWN * 0.3),
            GrowArrow(arr_tlock_c), FadeIn(dek_tlock_c, shift=DOWN * 0.3),
        )
        self.wait(0.5)
        self.play(FadeOut(step2))

        # ------------------------------------------------------------------ #
        # Step 3: IBE-seal DEK_client
        # ------------------------------------------------------------------ #
        step3 = Text("③ IBE-seal DEK_client under tlock round key → C_tlock", font_size=14, color=C_DIM)
        step3.to_edge(DOWN, buff=1.8)
        self.play(Write(step3))

        ibe_locked = key_block("C_tlock\n(IBE-sealed)", color=C_TLOCK_IBE, width=2.5, height=0.75, opacity=0.2)
        ibe_locked.move_to(LEFT * 5.2 + DOWN * 1.8)

        padlock = Text("🔒", font_size=20)
        padlock.next_to(ibe_locked, RIGHT, buff=0.1)
        clock = Text("⏰", font_size=16)
        clock.next_to(padlock, RIGHT, buff=0.05)

        arr_ibe = Arrow(dek_client.get_bottom(), ibe_locked.get_top(), color=C_TLOCK_IBE, buff=0.05, stroke_width=2)
        ibe_note = Text("IBE-seal( round_pub_key[r], DEK_client )", font_size=11, color=C_TLOCK_IBE)
        ibe_note.next_to(arr_ibe, RIGHT, buff=0.1)

        self.play(GrowArrow(arr_ibe), FadeIn(ibe_locked), FadeIn(padlock), FadeIn(clock), Write(ibe_note))
        self.wait(0.5)
        self.play(FadeOut(step3), FadeOut(ibe_note))

        # ------------------------------------------------------------------ #
        # Step 4: ECDH-wrap DEK to recipient (shown as padlock)
        # ------------------------------------------------------------------ #
        step4 = Text("④ ECDH-wrap DEK → W_cap (iOS path)  &  DEK_client → W_blind (Android/web)", font_size=13, color=C_DIM)
        step4.to_edge(DOWN, buff=1.8)
        self.play(Write(step4))

        w_cap = key_block("W_cap\n(ECDH-wrap DEK)", color=C_DEK, width=2.6, height=0.75, opacity=0.2)
        w_cap.move_to(LEFT * 3.5 + DOWN * 1.8)

        w_blind = key_block("W_blind\n(ECDH-wrap DEK_client)", color=C_CLIENT, width=2.8, height=0.75, opacity=0.2)
        w_blind.move_to(LEFT * 1.0 + DOWN * 1.8)

        arr_wcap = Arrow(dek.get_bottom() + DOWN * 0.4, w_cap.get_top(), color=C_DEK, buff=0.05, stroke_width=2)
        arr_wblind = Arrow(dek_client.get_bottom() + DOWN * 0.4, w_blind.get_top(), color=C_CLIENT, buff=0.05, stroke_width=2)

        self.play(
            GrowArrow(arr_wcap), FadeIn(w_cap, shift=DOWN * 0.2),
            GrowArrow(arr_wblind), FadeIn(w_blind, shift=DOWN * 0.2),
        )
        self.wait(0.5)
        self.play(FadeOut(step4))

        # ------------------------------------------------------------------ #
        # Step 5: DEK_tlock sent to server
        # ------------------------------------------------------------------ #
        step5 = Text("⑤ DEK_tlock sent to server  (server stores but cannot decrypt)", font_size=14, color=C_DIM)
        step5.to_edge(DOWN, buff=1.8)
        self.play(Write(step5))

        dek_tlock_srv = key_block("DEK_tlock", color=C_TLOCK, width=2.2)
        dek_tlock_srv.move_to(RIGHT * 3.0 + UP * 1.5)

        arr_to_srv = Arrow(
            dek_tlock_c.get_right(),
            dek_tlock_srv.get_left(),
            color=C_TLOCK,
            buff=0.05,
            stroke_width=2,
        )
        arr_to_srv.add_tip()

        self.play(GrowArrow(arr_to_srv), FadeIn(dek_tlock_srv, shift=RIGHT * 0.3))

        c_tlock_srv = key_block("C_tlock", color=C_TLOCK_IBE, width=2.2, opacity=0.2)
        c_tlock_srv.move_to(RIGHT * 3.0 + UP * 0.4)

        arr_ctlock_srv = Arrow(
            ibe_locked.get_right(),
            c_tlock_srv.get_left(),
            color=C_TLOCK_IBE,
            buff=0.05,
            stroke_width=2,
        )
        self.play(GrowArrow(arr_ctlock_srv), FadeIn(c_tlock_srv, shift=RIGHT * 0.3))

        w_cap_srv = key_block("W_cap, W_blind", color=C_CLIENT, width=2.6, opacity=0.2)
        w_cap_srv.move_to(RIGHT * 3.0 + DOWN * 0.7)

        arr_wrap_srv = Arrow(
            w_blind.get_right(),
            w_cap_srv.get_left(),
            color=C_CLIENT,
            buff=0.05,
            stroke_width=2,
        )
        self.play(GrowArrow(arr_wrap_srv), FadeIn(w_cap_srv, shift=RIGHT * 0.3))
        self.wait(0.5)
        self.play(FadeOut(step5))

        # ------------------------------------------------------------------ #
        # Step 6: Final state annotation — server-blindness
        # ------------------------------------------------------------------ #
        blind_note = Text(
            "Server holds:  DEK_tlock  +  C_tlock  +  W_cap  +  W_blind\n"
            "Server does NOT hold:  DEK  or  DEK_client\n"
            "→  Server-blind before and during delivery",
            font_size=13,
            color=YELLOW_D,
            line_spacing=1.4,
        )
        blind_note.to_edge(DOWN, buff=0.2)
        self.play(FadeIn(blind_note, shift=UP * 0.2))
        self.wait(2.0)

        # Highlight server region
        highlight = SurroundingRectangle(server_region, color=YELLOW_D, buff=0.1, stroke_width=3)
        self.play(Create(highlight))
        self.wait(1.0)
        self.play(FadeOut(highlight))
        self.wait(1.5)

        # ------------------------------------------------------------------ #
        # Fade out
        # ------------------------------------------------------------------ #
        self.play(
            *[FadeOut(m) for m in self.mobjects],
            run_time=1.2,
        )
