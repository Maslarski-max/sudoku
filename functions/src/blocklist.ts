// Matched anywhere in the compact form (letters only, after leet mapping).
export const BLOCKED_SUBSTRINGS = [
  "fuck", "shit", "cunt", "nigger", "nigga", "faggot", "bitch", "whore", "rapist",
  "nazi", "hitler", "pussy", "porn", "penis", "vagina", "dildo", "wank", "killyourself",
  "jebem", "jebo", "jebi", "jebac", "picka", "kurac", "kurc", "pizda", "pizdu", "drolja",
  "kurva", "peder", "govno", "sranje", "mamicu", "fotze", "hurensohn", "wichser",
  "arschloch", "schlampe", "ficken",
];

// Matched only as whole tokens (split on space/_/./-), after leet mapping.
export const BLOCKED_WORDS = [
  "ass", "arse", "fag", "cum", "dick", "cock", "pedo", "sex", "tit", "tits", "slut",
  "nutte", "fick", "jeb", "kys", "admin", "administrator", "moderator", "mod", "support",
  "official", "staff", "devin",
];
