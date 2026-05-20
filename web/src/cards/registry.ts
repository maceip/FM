/**
 * Central registry of the 12 FM cards. Each entry pairs a DOM component with
 * the pixel size it renders at — the texture pipeline and the R3F meshes both
 * read dimensions from here so a card only has to be described once.
 */
import type { ComponentType } from "react";
import {
  BROWSER_HEIGHT,
  BROWSER_WIDTH,
  CARD_HEIGHT,
  CARD_WIDTH,
} from "./CardShell";
import { LiveCallAgent } from "./LiveCallAgent";
import { DraftSend } from "./DraftSend";
import { SummaryOutlineNote } from "./SummaryOutlineNote";
import { ChecklistActionItems } from "./ChecklistActionItems";
import { TimerRemindersEvent } from "./TimerRemindersEvent";
import { RouteAssist } from "./RouteAssist";
import { MusicAudioQueue } from "./MusicAudioQueue";
import { ContactPersonContext } from "./ContactPersonContext";
import { ImageMediaSearch } from "./ImageMediaSearch";
import { ProvenanceCitations } from "./ProvenanceCitations";
import { ApproveUndo } from "./ApproveUndo";
import { MiniBrowser } from "./MiniBrowser";

export interface CardDef {
  id: string;
  label: string;
  Component: ComponentType;
  width: number;
  height: number;
}

const grid = (id: string, label: string, Component: ComponentType): CardDef => ({
  id,
  label,
  Component,
  width: CARD_WIDTH,
  height: CARD_HEIGHT,
});

export const CARDS: CardDef[] = [
  grid("live-call", "Live Call Agent", LiveCallAgent),
  grid("draft-send", "Draft + Send", DraftSend),
  grid("summary", "Summary / Outline / Note", SummaryOutlineNote),
  grid("checklist", "Checklist / Action Items", ChecklistActionItems),
  grid("timer", "Timer / Reminders / Event", TimerRemindersEvent),
  grid("route", "Route Assist", RouteAssist),
  grid("music", "Music / Audio Queue", MusicAudioQueue),
  grid("contact", "Contact / Person Context", ContactPersonContext),
  grid("image-search", "Image / Media Search", ImageMediaSearch),
  grid("citations", "Provenance / Citations", ProvenanceCitations),
  grid("approve", "Approve / Undo", ApproveUndo),
  {
    id: "browser",
    label: "Mini Browser",
    Component: MiniBrowser,
    width: BROWSER_WIDTH,
    height: BROWSER_HEIGHT,
  },
];

export const cardAspect = (card: CardDef) => card.width / card.height;
