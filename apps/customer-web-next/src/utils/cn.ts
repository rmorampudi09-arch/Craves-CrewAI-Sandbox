// Re-export of the shadcn/ui class-merge helper.
// Physical implementation stays at src/lib/utils.ts because 45+ generated
// shadcn/ui primitives in src/components/ui import "@/lib/utils" directly.
// Import from here ("@/utils/cn") in any NEW code you write.
export { cn } from "@/lib/utils";
