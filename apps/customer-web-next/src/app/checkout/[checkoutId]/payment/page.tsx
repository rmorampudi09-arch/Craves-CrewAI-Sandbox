import { RazorpayPayment } from "@/components/checkout/RazorpayPayment";
export const metadata = { title: "Secure Payment", robots: { index: false, follow: false } };
export default async function CheckoutPaymentPage({ params }: { params: Promise<{ checkoutId: string }> }) {
  const { checkoutId } = await params;
  return <RazorpayPayment checkoutId={checkoutId} />;
}
