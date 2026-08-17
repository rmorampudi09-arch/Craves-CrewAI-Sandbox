import { PolicySection, PublicPolicyPage } from "@/components/legal/PublicPolicyPage";

export const dynamic = "force-dynamic";

export const metadata = {
  title: "Contact Us | Craves",
  description: "Contact Craves for customer support, order help, and business enquiries.",
};

function publicSupportPhone(): string | null {
  const value = process.env.CRAVES_PUBLIC_SUPPORT_PHONE?.trim() ?? "";
  const digits = value.replace(/\D/g, "");
  return digits.length >= 10 && digits.length <= 13 ? value : null;
}

function registeredBusinessName(): string | null {
  const value = process.env.CRAVES_REGISTERED_BUSINESS_NAME?.trim() ?? "";
  return value.length >= 2 ? value : null;
}

export default function ContactPage() {
  const supportPhone = publicSupportPhone();
  const businessName = registeredBusinessName();

  return (
    <PublicPolicyPage
      eyebrow="Contact us"
      title="We’re here to help."
      intro="Craves is a Hyderabad-based homemade food marketplace. Use the details below for order support, account help, payment/refund questions, or business enquiries."
    >
      <PolicySection title="Customer support">
        <p>
          For order, payment, refund, account, chef-order, or delivery help, email{" "}
          <a className="font-semibold text-[#F62E18] underline" href="mailto:support@craves.in">
            support@craves.in
          </a>.
        </p>
        {supportPhone ? (
          <p>
            Support phone:{" "}
            <a
              className="font-semibold text-[#F62E18] underline"
              href={`tel:${supportPhone.replace(/[^+\d]/g, "")}`}
            >
              {supportPhone}
            </a>
          </p>
        ) : (
          <p data-craves-contact-phone-status="pending" className="font-semibold text-[#B54708]">
            The public support phone is pending production configuration. Razorpay live activation remains blocked until it is configured.
          </p>
        )}
        <p>Please include your Craves order reference when one is available. Never send a card number, CVV, UPI PIN, OTP, password, or API secret by email.</p>
      </PolicySection>

      <PolicySection title="Business and partnerships">
        <p>
          For partnerships, media, home-chef programmes, or business enquiries, email{" "}
          <a className="font-semibold text-[#F62E18] underline" href="mailto:contact@craves.in">
            contact@craves.in
          </a>.
        </p>
        {businessName ? (
          <p>Registered business / merchant name: <strong>{businessName}</strong></p>
        ) : (
          <p data-craves-business-name-status="pending" className="font-semibold text-[#B54708]">
            The registered merchant name is pending production configuration. Razorpay live activation remains blocked until it is configured.
          </p>
        )}
      </PolicySection>

      <PolicySection title="Location">
        <p>Craves · Hyderabad, Telangana, India.</p>
      </PolicySection>
    </PublicPolicyPage>
  );
}
