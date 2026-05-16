"""
capsule_delivery.py — Heirlooms Capsule Delivery Animation
==========================================================

Animates the M11 tlock capsule delivery process:
  1. Clock advances to t_open
  2. drand beacon publishes round key (public event)
  3. Server confirms IBE gate is open (but does NOT return DEK_client)
  4. Server serves DEK_tlock to the authenticated Android/web recipient
  5. Client ECDH-unwraps W_blind → DEK_client
  6. Client XORs: DEK_client XOR DEK_tlock = DEK
  7. Client decrypts content with DEK
  8. Server-blindness annotation: server had DEK_tlock but never DEK_client or DEK

Requires Manim Community Edition >= 0.18:
    pip install manim

Render commands:
    manim -pql capsule_delivery.py CapsuleDelivery   # Low quality preview
    manim -pqh capsule_delivery.py CapsuleDelivery   # High quality
    manim -pql --format=gif capsule_delivery.py CapsuleDelivery

Outputs to: ./media/videos/capsule_delivery/
"""

from manim import (
    Scene, VGroup, Rectangle, RoundedRectangle, Text, Arrow, Line,
    Dot, Circle, MathTex, DecimalNumber,
    FadeIn, FadeOut, GrowArrow, Write, Create, AnimationGroup,
    Transform, ReplacementTransform, Indicate, Flash, Wiggle,
    UP, DOWN, LEFT, RIGHT, ORIGIN,
    WHITE, BLUE, GREEN, YELLOW, ORANGE, RED, GREY, BLACK,
    BLUE_D, BLUE_A, GREEN_D, YELLOW_D, ORANGE_D, RED_D,
    config,
    SurroundingRectangle, ValueTracker, always_redraw,
)
import numpy as np

# Colour palette
C_DEK       = GREEN_D
C_CLIENT    = BLUE_D
C_TLOCK     = RED_D
C_TLOCK_IBE = ORANGE
C_SERVER    = GREY
C_DRAND     = PURPLE_D = "#9B59B6"
C_LABEL     = WHITE
C_DIM       = "#888888"
C_XOR       = YELLOW_D
C_SUCCESS   = "#2ECC71"


def key_block(label: str, color=WHITE, width=2.0, height=0.55, opacity=0.18):
    box = RoundedRectangle(
        corner_radius=0.1,
        width=width, height=height,
        color=color, fill_color=color,
        fill_opacity=opacity, stroke_width=2,
    )
    txt = Text(label, font_size=16, color=color, weight="BOLD")
    txt.move_to(box)
    return VGroup(box, txt)


def party_label(text: str, color=WHITE):
    return Text(text, font_size=16, color=color, weight="BOLD")


class CapsuleDelivery(Scene):
    def construct(self):

        # ------------------------------------------------------------------ #
        # Layout
        # Three vertical lanes: Client | Server | drand Beacon
        # ------------------------------------------------------------------ #
        LANE_Y_TOP = 3.0

        # Lane dividers
        div1 = Line(UP * LANE_Y_TOP + LEFT * 1.5, DOWN * 3.2 + LEFT * 1.5, color=C_DIM, stroke_width=1, stroke_opacity=0.5)
        div2 = Line(UP * LANE_Y_TOP + RIGHT * 3.0, DOWN * 3.2 + RIGHT * 3.0, color=C_DIM, stroke_width=1, stroke_opacity=0.5)

        lbl_client = party_label("Client (Android/web)", color=BLUE_D)
        lbl_client.move_to(LEFT * 4.0 + UP * LANE_Y_TOP)
        lbl_server = party_label("Server", color=C_SERVER)
        lbl_server.move_to(RIGHT * 0.7 + UP * LANE_Y_TOP)
        lbl_drand = party_label("drand Beacon", color=C_DRAND)
        lbl_drand.move_to(RIGHT * 4.5 + UP * LANE_Y_TOP)

        title = Text("Capsule Delivery — tlock path", font_size=28, color=WHITE)
        title.to_edge(UP, buff=0.05)

        self.play(Write(title), FadeIn(div1), FadeIn(div2))
        self.play(FadeIn(lbl_client), FadeIn(lbl_server), FadeIn(lbl_drand))
        self.wait(0.3)

        # Pre-delivery state labels
        srv_holds = Text(
            "Server holds:\n• DEK_tlock\n• C_tlock (IBE)\n• W_cap, W_blind",
            font_size=12, color=C_DIM, line_spacing=1.3,
        )
        srv_holds.move_to(RIGHT * 0.7 + UP * 1.8)
        self.play(FadeIn(srv_holds))
        self.wait(0.4)

        # ------------------------------------------------------------------ #
        # Step 1: Clock advances to t_open
        # ------------------------------------------------------------------ #
        step_lbl = Text("", font_size=14, color=C_DIM)
        step_lbl.to_edge(DOWN, buff=0.15)

        clock_text = Text("⏰  t < t_open", font_size=22, color=C_DIM)
        clock_text.move_to(RIGHT * 4.5 + UP * 1.5)
        self.play(FadeIn(clock_text))
        self.wait(0.5)

        clock_open = Text("⏰  t = t_open  !", font_size=22, color=YELLOW_D)
        clock_open.move_to(clock_text.get_center())
        step1_lbl = Text("① Clock reaches t_open — drand publishes round key sk_r", font_size=14, color=C_DIM)
        step1_lbl.to_edge(DOWN, buff=0.15)
        self.play(Write(step1_lbl))
        self.play(ReplacementTransform(clock_text, clock_open))
        self.wait(0.4)

        # drand publishes
        round_key = key_block("sk_r  (round key)", color=C_DRAND, width=2.4)
        round_key.move_to(RIGHT * 4.5 + UP * 0.4)
        broadcast_arrow = Arrow(
            clock_open.get_bottom(),
            round_key.get_top(),
            color=C_DRAND, buff=0.05, stroke_width=2,
        )
        self.play(GrowArrow(broadcast_arrow), FadeIn(round_key, shift=DOWN * 0.2))
        self.wait(0.3)
        self.play(FadeOut(step1_lbl))

        # ------------------------------------------------------------------ #
        # Step 2: Server confirms IBE gate open
        # ------------------------------------------------------------------ #
        step2_lbl = Text("② Server calls IBE-open(sk_r, C_tlock) → confirms gate open (result not returned to client)", font_size=12, color=C_DIM)
        step2_lbl.to_edge(DOWN, buff=0.15)
        self.play(Write(step2_lbl))

        # Arrow: drand round key → server
        arr_drand_srv = Arrow(
            round_key.get_left(),
            RIGHT * 1.5 + UP * 0.3,
            color=C_DRAND, buff=0.05, stroke_width=2,
        )
        gate_check = Text("IBE-open ✓\n(gate open)", font_size=13, color=YELLOW_D)
        gate_check.move_to(RIGHT * 0.7 + UP * 0.3)
        self.play(GrowArrow(arr_drand_srv), FadeIn(gate_check))
        self.wait(0.5)
        self.play(FadeOut(step2_lbl))

        # ------------------------------------------------------------------ #
        # Step 3: Server serves DEK_tlock to client
        # ------------------------------------------------------------------ #
        step3_lbl = Text("③ Server serves DEK_tlock to authenticated recipient  (never logs this value)", font_size=13, color=C_DIM)
        step3_lbl.to_edge(DOWN, buff=0.15)
        self.play(Write(step3_lbl))

        dek_tlock_srv = key_block("DEK_tlock", color=C_TLOCK, width=2.0)
        dek_tlock_srv.move_to(RIGHT * 0.7 + DOWN * 0.7)

        arr_tlock_to_client = Arrow(
            dek_tlock_srv.get_left(),
            LEFT * 2.8 + DOWN * 0.7,
            color=C_TLOCK, buff=0.05, stroke_width=2,
        )
        dek_tlock_client = key_block("DEK_tlock", color=C_TLOCK, width=2.0)
        dek_tlock_client.move_to(LEFT * 4.0 + DOWN * 0.7)

        self.play(FadeIn(dek_tlock_srv))
        self.play(GrowArrow(arr_tlock_to_client), FadeIn(dek_tlock_client, shift=LEFT * 0.3))
        self.wait(0.4)
        self.play(FadeOut(step3_lbl))

        # ------------------------------------------------------------------ #
        # Step 4: Client ECDH-unwraps W_blind → DEK_client
        # ------------------------------------------------------------------ #
        step4_lbl = Text("④ Client ECDH-unwraps W_blind  →  recovers DEK_client (the mask)", font_size=13, color=C_DIM)
        step4_lbl.to_edge(DOWN, buff=0.15)
        self.play(Write(step4_lbl))

        w_blind_label = Text("W_blind\n(stored)", font_size=13, color=C_CLIENT)
        w_blind_label.move_to(LEFT * 4.0 + UP * 0.8)
        self.play(FadeIn(w_blind_label))

        dek_client = key_block("DEK_client", color=C_CLIENT, width=2.2)
        dek_client.move_to(LEFT * 4.0 + DOWN * 1.8)

        arr_unwrap = Arrow(
            w_blind_label.get_bottom(),
            dek_client.get_top(),
            color=C_CLIENT, buff=0.05, stroke_width=2,
        )
        unwrap_note = Text("ECDH-unwrap(priv, W_blind)", font_size=11, color=C_CLIENT)
        unwrap_note.next_to(arr_unwrap, RIGHT, buff=0.08)

        self.play(GrowArrow(arr_unwrap), FadeIn(dek_client, shift=DOWN * 0.2), FadeIn(unwrap_note))
        self.wait(0.5)
        self.play(FadeOut(step4_lbl), FadeOut(unwrap_note))

        # ------------------------------------------------------------------ #
        # Step 5: Client XORs
        # ------------------------------------------------------------------ #
        step5_lbl = Text("⑤ Client computes:  DEK = DEK_client  ⊕  DEK_tlock", font_size=14, color=C_DIM)
        step5_lbl.to_edge(DOWN, buff=0.15)
        self.play(Write(step5_lbl))

        xor_gate = Circle(radius=0.28, color=C_XOR, stroke_width=2)
        xor_sym = MathTex(r"\oplus", color=C_XOR, font_size=30)
        xor_sym.move_to(xor_gate)
        xor_group = VGroup(xor_gate, xor_sym)
        xor_group.move_to(LEFT * 4.0 + DOWN * 2.9)

        arr_client_xor = Arrow(dek_client.get_bottom(), xor_group.get_top() + LEFT * 0.1, color=C_CLIENT, buff=0.05, stroke_width=2)
        arr_tlock_xor = Arrow(dek_tlock_client.get_bottom() + DOWN * 0.1, xor_group.get_top() + RIGHT * 0.1, color=C_TLOCK, buff=0.05, stroke_width=2)

        self.play(GrowArrow(arr_client_xor), GrowArrow(arr_tlock_xor), FadeIn(xor_group))

        dek_result = key_block("DEK ✓", color=C_SUCCESS, width=2.2, opacity=0.3)
        dek_result.move_to(LEFT * 4.0 + DOWN * 3.9)
        arr_xor_dek = Arrow(xor_group.get_bottom(), dek_result.get_top(), color=C_SUCCESS, buff=0.05, stroke_width=2)
        self.play(GrowArrow(arr_xor_dek), FadeIn(dek_result, shift=DOWN * 0.2))
        self.play(Flash(dek_result, color=C_SUCCESS, line_length=0.3, flash_radius=0.6))
        self.wait(0.5)
        self.play(FadeOut(step5_lbl))

        # ------------------------------------------------------------------ #
        # Step 6: Decrypt content
        # ------------------------------------------------------------------ #
        step6_lbl = Text("⑥ Client decrypts content:  AES-256-GCM(DEK, nonce, ciphertext)", font_size=13, color=C_DIM)
        step6_lbl.to_edge(DOWN, buff=0.15)
        self.play(Write(step6_lbl))

        content_revealed = Text("📂 Content decrypted", font_size=20, color=C_SUCCESS)
        content_revealed.move_to(LEFT * 4.0 + DOWN * 4.7)
        self.play(FadeIn(content_revealed, shift=DOWN * 0.2))
        self.wait(0.5)
        self.play(FadeOut(step6_lbl))

        # ------------------------------------------------------------------ #
        # Step 7: Server-blindness annotation
        # ------------------------------------------------------------------ #
        blind_box = SurroundingRectangle(
            VGroup(lbl_server, srv_holds, gate_check, dek_tlock_srv),
            color=YELLOW_D, buff=0.2, stroke_width=2,
        )
        blind_note = Text(
            "SERVER-BLINDNESS:\n"
            "Server held DEK_tlock (one XOR half)\n"
            "Server NEVER had DEK_client (locked in IBE)\n"
            "Server NEVER reconstructed DEK",
            font_size=12, color=YELLOW_D, line_spacing=1.4,
        )
        blind_note.to_edge(DOWN, buff=0.15)

        self.play(Create(blind_box), Write(blind_note))
        self.wait(2.5)

        # ------------------------------------------------------------------ #
        # Fade out
        # ------------------------------------------------------------------ #
        self.play(
            *[FadeOut(m) for m in self.mobjects],
            run_time=1.2,
        )
