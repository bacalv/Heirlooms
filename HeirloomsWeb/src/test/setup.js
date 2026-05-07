import '@testing-library/jest-dom'

// JSDOM doesn't implement getTotalLength on SVG elements. Patch Element.prototype
// so animation components that call it in effects don't throw. Real browsers always
// provide it on SVGGeometryElement; this stub is test-only.
if (typeof window !== 'undefined') {
  Element.prototype.getTotalLength = Element.prototype.getTotalLength || (() => 100)
}

// JSDOM doesn't implement window.matchMedia — provide a no-motion default.
// Individual tests that need to test reduced-motion override this with vi.fn().
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = () => ({
    matches: false,
    addEventListener: () => {},
    removeEventListener: () => {},
  })
}
