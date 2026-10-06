# The limits of attestation

What `catalogue/attested/*.jsonl` actually proves, and what it does not.

Written 2026-10-05, immediately after the harvest, because the finding here is
load-bearing for every Tier 0 decision and it is easy to forget later.

## The finding

One contributor, **CK**, accounts for roughly a third of the attested
candidates in **every** harvested language:

    ara  197/574  34%      fra  295/915  32%
    hin  103/273  37%      ind  309/712  43%
    spa  322/896  35%      swh   65/175  37%

That is not six independent language communities. It is one person.

CK has 1000+ sentences in **20 languages** — Swahili, Tamil, Thai, Japanese,
Korean, Mandarin. Nobody is a native speaker of 19 languages. CK is
almost certainly a competent multilingual translator working from English.

The Thai evidence is direct and does not rely on inference:

    CK Thai sentences sampled:  250
    with no politeness particle: 249

A native Thai speaker uses ครับ (male) or ค่ะ (female) almost universally; the
app's own Thai spec records that this particle marks the speaker's gender and
that it has no verified neutral form. 249 of 250 sentences omitting it is not
a native speaker writing briefly. It is someone translating word lists and
sentence pairs.

CK's Swahili shows the same signature: `Hujambo, hii ndiyo idara ya
wafanyakazi?` — "Hello, is this the personnel department?" Tatoeba's
conversational corpus in every language contains internal links between
sentences; an unattached sentence like that one is clearly a list entry, not
something a person said in a conversation.

## What Tatoeba does NOT expose

There is **no nativeness signal** in the API. Not in the sentence object, and
not as a filter:

    ?native=yes     -> ignored, count unchanged at 1000, first result different
    ?own=yes        -> ignored, same
    ?is_native=1    -> ignored, same
    ?username=CK    -> ignored, first result was Somsak, NOT CK

Those parameters are silently accepted and silently ignored. A filter that
returns plausible-looking data while doing nothing is worse than one that
errors, because it invites the belief that the distinction was made. It was not.

## Therefore

`attested` means **a named contributor wrote this sentence**, and that is all
it means. It does NOT mean:

  - the author is a native speaker of that language
  - the sentence is the natural way to say it
  - the sentence is regionally appropriate
  - the translation on the English side is accurate

It does positively mean: the sentence exists, is attributed, is traceable to a
sentence id, and was written deliberately by a person rather than generated.

## Why this still beats authoring

The comparison is not "attested is correct". It is "attested is a real
sentence written by a real person" versus "authored is my own guess". When
both are unreliable, the one with a name attached to it is the one worth
learning from, and the one worth checking when Kedhar flags it.

But the honest ranking puts a native contributor above a polyglot translator,
and the harvester cannot tell them apart. So selection must not let one
contributor dominate: see the per-contributor cap in `select.mjs`.

## The deeper limit

Even a genuine native speaker writing a sentence in Tatoeba's conversational
corpus is not a native speaker confirming a phrase for a traveller. Tatoeba
records what people wrote. It does not record what a person would say to a
shopkeeper, and the harvest has already shown that concept membership does not
imply phrasebook-worthiness — the concept `i_want` matched "Aku tidak ingin
hidup selamanya" (I don't want to live forever).

The irreducible gap stays open. What changed is that it is now *named* instead
of being assumed away.

## Dari was removed on 2026-10-06

Recorded here because it is the clearest case of the rule this project is built on.

Tatoeba has no Dari. The code that looks like it should — `prs` — reports 721 results and returns
an unrelated scatter of languages; `fas` reports 3,465 and delivers zero Persian. Only `pes`
filters, verified by every result it returns carrying `lang_tag=pes` and nothing else. And `pes`
is Iranian Persian, a different variety from the Afghan Dari: mutually intelligible, not
identical.

Filling the Dari entry from `pes` would have produced a full-looking language whose material came
from a source that does not say what it is. Eighty-one verified pairs were available. Shipping
them under a Dari label is a quiet overstatement, which is the specific failure the evidence model
exists to prevent, so the language was dropped instead.

If a labelled Iranian-Persian entry is ever wanted, it should be its own language with its own
name in the catalogue — not Dari with Persian inside it.
