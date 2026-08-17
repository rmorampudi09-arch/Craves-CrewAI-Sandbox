/** One "label ... value" line in a bill-details / order-summary card. */
export function PriceRow({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div
      className={`flex items-center justify-between ${bold ? "font-display text-base font-bold text-ink" : "text-ink"}`}
    >
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

export default PriceRow;
