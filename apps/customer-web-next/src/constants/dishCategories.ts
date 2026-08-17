export const ALL_DISHES_CATEGORY = "All" as const;

/** Categories are returned by the live catalog; the UI always prepends All. */
export type DishCategory = string;
