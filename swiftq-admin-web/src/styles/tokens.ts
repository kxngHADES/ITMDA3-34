// Generated from tokens.json by build.py. Do not edit by hand.
// Numbers are px (radius, space, size, typography) and ms (duration).

export const tokens = {
  "color": {
    "blue": {
      "50": "#F0F7FF",
      "100": "#D6E9FF",
      "300": "#66AFFF",
      "system": "#007AFF",
      "text": "#0062CC",
      "pressed": "#0051A8"
    },
    "neutral": {
      "white": "#FFFFFF",
      "grouped": "#F2F2F7",
      "separator": "#E5E5EA",
      "tertiary": "#8E8E93",
      "secondary": "#6C6C70",
      "label": "#1C1C1E"
    },
    "status": {
      "success": {
        "fill": "#34C759",
        "text": "#248A3D",
        "tint": "#E8F8EC"
      },
      "warning": {
        "fill": "#FF9500",
        "text": "#C93400",
        "tint": "#FFF4E5"
      },
      "error": {
        "fill": "#FF3B30",
        "text": "#D70015",
        "tint": "#FFEBEA"
      },
      "progress": {
        "fill": "#5856D6",
        "text": "#3634A3",
        "tint": "#EEEEFB"
      }
    },
    "brand": {
      "navy": "#0B2F7A",
      "night": "#111522",
      "gradient-start": "#62B8FF",
      "gradient-mid": "#0A7DFF",
      "gradient-end": "#0040C8",
      "cross-tint": "#DCEBFF"
    }
  },
  "semantic": {
    "bg": {
      "screen": "#FFFFFF",
      "grouped": "#F2F2F7",
      "card": "#FFFFFF",
      "selected": "#F0F7FF",
      "primary": "#007AFF"
    },
    "text": {
      "primary": "#1C1C1E",
      "secondary": "#6C6C70",
      "placeholder": "#8E8E93",
      "link": "#0062CC",
      "on-primary": "#FFFFFF",
      "danger": "#D70015"
    },
    "action": {
      "primary": "#007AFF",
      "primary-pressed": "#0051A8",
      "danger": "#D70015"
    },
    "border": {
      "default": "#E5E5EA",
      "selected": "#007AFF"
    },
    "icon": {
      "default": "#007AFF",
      "muted": "#8E8E93"
    },
    "ticket": {
      "waiting": {
        "bg": "#D6E9FF",
        "fg": "#0062CC"
      },
      "almost": {
        "bg": "#FFF4E5",
        "fg": "#C93400"
      },
      "called": {
        "bg": "#007AFF",
        "fg": "#FFFFFF"
      },
      "in-progress": {
        "bg": "#EEEEFB",
        "fg": "#3634A3"
      },
      "completed": {
        "bg": "#E8F8EC",
        "fg": "#248A3D"
      },
      "cancelled": {
        "bg": "#FFEBEA",
        "fg": "#D70015"
      },
      "no-show": {
        "bg": "#FFEBEA",
        "fg": "#D70015"
      }
    },
    "priority": {
      "verified": {
        "bg": "#E8F8EC",
        "fg": "#248A3D"
      },
      "pending": {
        "bg": "#F2F2F7",
        "fg": "#6C6C70"
      }
    }
  },
  "font": {
    "family": "Inter, Roboto, system-ui, sans-serif"
  },
  "typography": {
    "large-title": {
      "fontWeight": 700,
      "fontSize": 34,
      "lineHeight": 41,
      "letterSpacing": 0
    },
    "title-1": {
      "fontWeight": 700,
      "fontSize": 28,
      "lineHeight": 34,
      "letterSpacing": 0
    },
    "title-3": {
      "fontWeight": 600,
      "fontSize": 20,
      "lineHeight": 25,
      "letterSpacing": 0
    },
    "headline": {
      "fontWeight": 600,
      "fontSize": 17,
      "lineHeight": 22,
      "letterSpacing": 0
    },
    "body": {
      "fontWeight": 400,
      "fontSize": 17,
      "lineHeight": 22,
      "letterSpacing": 0
    },
    "callout": {
      "fontWeight": 500,
      "fontSize": 16,
      "lineHeight": 21,
      "letterSpacing": 0
    },
    "subhead": {
      "fontWeight": 400,
      "fontSize": 15,
      "lineHeight": 20,
      "letterSpacing": 0
    },
    "footnote": {
      "fontWeight": 400,
      "fontSize": 13,
      "lineHeight": 18,
      "letterSpacing": 0
    },
    "caption": {
      "fontWeight": 400,
      "fontSize": 12,
      "lineHeight": 16,
      "letterSpacing": 0
    },
    "caption-emphasised": {
      "fontWeight": 600,
      "fontSize": 12,
      "lineHeight": 16,
      "letterSpacing": 0
    },
    "ticket-number": {
      "fontWeight": 700,
      "fontSize": 76,
      "lineHeight": 88,
      "letterSpacing": -1.5
    }
  },
  "radius": {
    "sm": 12.0,
    "md": 14.0,
    "lg": 16.0,
    "xl": 20.0,
    "2xl": 28.0,
    "full": 9999.0
  },
  "space": {
    "1": 4.0,
    "2": 8.0,
    "3": 12.0,
    "4": 16.0,
    "5": 20.0,
    "6": 24.0,
    "8": 32.0,
    "10": 40.0,
    "14": 56.0
  },
  "size": {
    "touch-min": 48.0,
    "button": 56.0,
    "button-lg": 64.0,
    "icon": 24.0,
    "icon-lg": 32.0,
    "icon-tile": 56.0,
    "tab-bar": 84.0,
    "icon-stroke": 2.0
  },
  "shadow": {
    "primary-glow": "0px 10px 24px -6px #007AFF47",
    "card": "0px 2px 8px 0px #1C1C1E14"
  },
  "duration": {
    "fast": 150,
    "base": 250,
    "slow": 400
  }
} as const;

export type TicketStatus = keyof typeof tokens.semantic.ticket;
export default tokens;
