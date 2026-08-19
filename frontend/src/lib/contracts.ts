export type PageResponse<T> = {
  items: T[];
  nextPageOffset: number;
  hasMore: boolean;
};

export type Profile = {
  userId: string;
  email: string;
  role: string;
  shopName: string;
  city: string;
  state: string;
  createdAtEpochSeconds: number;
};

export type EventSummary = {
  id: string;
  name: string;
  city: string;
  state: string;
  venue: string;
  startDate: string;
  endDate: string;
  description: string;
  createdAtEpochSeconds: number;
  updatedAtEpochSeconds: number;
};

export type EventRegistration = {
  id: string;
  eventId: string;
  userId: string;
  role: string;
  booth: string;
  registeredAtEpochSeconds: number;
  vendor: PublicVendor | null;
};

export type VendorRoster = {
  vendors: EventRegistration[];
};

export type PublicVendor = {
  userId: string;
  shopName: string;
  city: string;
  state: string;
  role: string;
  booth: string;
};

export type InventoryItem = {
  id: string;
  vendorId: string;
  cardId: string;
  eventId: string;
  condition: string;
  gradingCompany: string;
  grade: string;
  quantity: number;
  askingPrice: string;
  priority: string;
  createdAtEpochSeconds: number;
  updatedAtEpochSeconds: number;
  vendor: PublicVendor | null;
};

export type WantedCard = {
  id: string;
  vendorId: string;
  cardId: string;
  minimumCondition: string;
  maxBuyPrice: string;
  quantityWanted: number;
  createdAtEpochSeconds: number;
  updatedAtEpochSeconds: number;
  vendor: PublicVendor | null;
};

export type CardSummary = {
  id: string;
  externalId: string;
  name: string;
  setId: string;
  setName: string;
  setSeries: string;
  rarity: string;
  imageUrl: string;
  imageUrlLarge: string;
  releaseDate: string;
};

export type CardSearchResponse = {
  cards: CardSummary[];
  nextPageToken: string;
};

export type CardBatchResponse = {
  cards: CardSummary[];
};

export type SetSummary = {
  id: string;
  name: string;
  series: string;
  cardCount: number;
};

export type Overlap = {
  id: string;
  eventId: string;
  buyerVendorId: string;
  sellerVendorId: string;
  cardId: string;
  inventoryItemId: string;
  wantedCardId: string;
  sellerCondition: string;
  minimumCondition: string;
  availableQuantity: number;
  quantityWanted: number;
  askingPrice: string;
  maxBuyPrice: string;
  inventoryPriority: string;
  score: string;
  active: boolean;
  createdAtEpochSeconds: number;
  updatedAtEpochSeconds: number;
  counterparty: PublicVendor | null;
};

export type SavedOverlap = {
  id: string;
  vendorId: string;
  overlap: Overlap;
  createdAtEpochSeconds: number;
};

export type OverlapInterest = {
  id: string;
  overlapId: string;
  interestedVendorId: string;
  counterpartyVendorId: string;
  eventId: string;
  score: string;
  status: string;
  createdAtEpochSeconds: number;
  updatedAtEpochSeconds: number;
  interestedVendor: PublicVendor | null;
};

export type NotificationItem = {
  id: string;
  eventId: string;
  trigger: string;
  overlapId: string;
  cardId: string;
  counterpartyVendorId: string;
  payloadJson: string;
  read: boolean;
  active: boolean;
  availableAtEpochSeconds: number;
  createdAtEpochSeconds: number;
  updatedAtEpochSeconds: number;
  counterparty: PublicVendor | null;
};

export type NotificationPreferences = {
  inAppEnabled: boolean;
  emailEnabled: boolean;
  overlapBuyListEnabled: boolean;
  overlapLiquidateEnabled: boolean;
  savedOverlapActiveEnabled: boolean;
  digestMode: string;
  mutedEventIds: string[];
  updatedAtEpochSeconds: number;
};

export type UnreadCount = {
  unreadCount: number;
};

export type ImportResolvedRow = {
  rowNumber: number;
  cardId: string;
  cardName: string;
  setName: string;
  condition: string;
  gradingCompany: string;
  grade: string;
  quantity: number;
  askingPrice: string;
  priority: string;
  confidence: number;
};

export type ImportCandidate = {
  cardId: string;
  cardName: string;
  setName: string;
  confidence: number;
};

export type ImportIssue = {
  rowNumber: number;
  cardName: string;
  setName: string;
  reason: string;
  candidates: ImportCandidate[];
};

export type ImportResponse = {
  importedItems: InventoryItem[];
  resolvedRows: ImportResolvedRow[];
  issues: ImportIssue[];
  committed: boolean;
};
