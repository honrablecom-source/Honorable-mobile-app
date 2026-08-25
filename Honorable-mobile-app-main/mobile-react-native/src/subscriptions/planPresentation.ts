/** Presentation copy only. Native/server TrustedEntitlementState is the access authority. */
export const planPresentation=[
  {tier:'FREE',tagline:'Find your photos.',storage:'15 GB',video:'Photo search only',features:['Natural-language photo search','OCR search','Confidence-aware results']},
  {tier:'PLUS',tagline:'Search photos and videos.',storage:'100 GB',video:'20 min / 4 videos',features:['Everything in Free','Video semantic search','Representative-frame search']},
  {tier:'PRO',tagline:'Powerful search and exact video moments.',storage:'350 GB',video:'45 min / 4 videos',features:['Everything in Plus','Exact video moments','Search refinement']},
  {tier:'SUPER',tagline:'Visual intelligence for your entire library.',storage:'700 GB',video:'120 min / 4 videos',features:['Everything in Pro','Advanced features · Coming soon']},
  {tier:'ULTIMATE',tagline:'Everything Honorable.',storage:'1 TB',video:'600 min / 4 videos',features:['Everything in Super','Highest fair-use allowance','Family architecture · up to 5']},
] as const;
