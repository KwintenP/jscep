package org.jscep.client;

import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used for storing CA and RA certificates.
 */
public final class CertStoreInspector {
    /**
     * The length of the minimum certificate path for an issuer.
     */
    private static final int CA_PATH_LENGTH = 0;
    /**
     * Retrieve only end entities.
     */
    private static final int ONLY_END_ENTITIES = -2;
    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory
            .getLogger(CertStoreInspector.class);
    /**
     * Length of the key usage array.
     */
    private static final int KEY_USAGE_LENGTH = 9;
    private static final Map<CertStore, CertStoreInspector> INSTANCES = new WeakHashMap<CertStore, CertStoreInspector>();

    private final X509Certificate signer;
    private final X509Certificate recipient;
    private final X509Certificate issuer;

    /**
     * @param signer
     *            the certificate of the message signing authority
     * @param recipient
     *            the certificate of the message recipient.
     * @param issuer
     *            the certificate of the certificate issuer.
     */
    private CertStoreInspector(final X509Certificate signer,
            final X509Certificate recipient, final X509Certificate issuer) {
        this.signer = signer;
        this.recipient = recipient;
        this.issuer = issuer;
    }

    /**
     * Returns the verifier certificate.
     * 
     * @return the verifier certificate.
     */
    public X509Certificate getSigner() {
        return signer;
    }

    /**
     * Returns the encrypter certificate.
     * 
     * @return the encrypter certificate.
     */
    public X509Certificate getRecipient() {
        return recipient;
    }

    /**
     * Returns the issuer certificate.
     * 
     * @return the issuer certificate.
     */
    public X509Certificate getIssuer() {
        return issuer;
    }

    /**
     * Inspects the given CertStore to extract an Authorities instance.
     * <p>
     * This method will inspect the given CertStore with pre-configured
     * selectors to match RA certificates for encryption and verification, plus
     * the issuing CA certificate.
     * <p>
     * If the CertStore only contains a single CA certificate, that certificate
     * will be used for all three roles.
     * 
     * @param store
     *            the store to inspect.
     * @return the Authorities instance.
     */
    public static CertStoreInspector getInstance(final CertStore store) {
        CertStoreInspector instance = INSTANCES.get(store);
        if (instance != null) {
            return instance;
        }
        try {
            Collection<? extends Certificate> certs = store
                    .getCertificates(null);
            LOGGER.debug("CertStore contains {} certificate(s):", certs.size());
            int i = 0;
            for (Certificate cert : certs) {
                X509Certificate x509 = (X509Certificate) cert;
                LOGGER.debug("{}. '[issuer={}; serial={}]'", new Object[] {
                        ++i, x509.getIssuerDN(), x509.getSerialNumber() });
            }

            LOGGER.debug("Looking for recipient entity");
            X509Certificate recipient = findRecipient(store);
            LOGGER.debug("Using [issuer={}; serial={}] for recipient entity",
                    recipient.getIssuerDN(), recipient.getSerialNumber());

            LOGGER.debug("Looking for message signing entity");
            X509Certificate signer = findSigner(store);
            LOGGER.debug(
                    "Using [issuer={}; serial={}] for message signing entity",
                    signer.getIssuerDN(), signer.getSerialNumber());

            LOGGER.debug("Looking for issuing entity");
            X509Certificate issuer = findIssuer(store);
            LOGGER.debug("Using [issuer={}; serial={}] for issuing entity",
                    issuer.getIssuerDN(), issuer.getSerialNumber());

            instance = new CertStoreInspector(signer, recipient, issuer);
            INSTANCES.put(store, instance);

            return instance;
        } catch (CertStoreException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds the certificate of the certificate issuer.
     * 
     * @param store
     *            the certificate store to inspect.
     * @return the certificate issuer's certificate.
     * @throws CertStoreException
     *             if the CertStore cannot be inspected
     */
    private static X509Certificate findIssuer(final CertStore store)
            throws CertStoreException {
        X509CertSelector selector = new X509CertSelector();
        selector.setBasicConstraints(CA_PATH_LENGTH);

        LOGGER.debug("Selecting certificate with basicConstraints pathLen > 0");
        Collection<? extends Certificate> certs = store
                .getCertificates(selector);
        if (certs.size() > 0) {
            LOGGER.debug(
                    "Found {} certificate(s) with basicConstraints pathLen > 0",
                    certs.size());
            return (X509Certificate) certs.iterator().next();
        } else {
            throw new RuntimeException("No CA certificates found");
        }
    }

    /**
     * Finds the certificate of the SCEP message object signer.
     * 
     * @param store
     *            the certificate store to inspect.
     * @return the signer's certificate.
     * @throws CertStoreException
     *             if the CertStore cannot be inspected
     */
    private static X509Certificate findSigner(final CertStore store)
            throws CertStoreException {
        boolean[] keyUsage = new boolean[KEY_USAGE_LENGTH];
        keyUsage[0] = true;
        X509CertSelector signingSelector = new X509CertSelector();
        signingSelector.setBasicConstraints(ONLY_END_ENTITIES);
        signingSelector.setKeyUsage(keyUsage);

        LOGGER.debug("Selecting certificate with keyUsage:digitalSignature");
        Collection<? extends Certificate> certs = store
                .getCertificates(signingSelector);
        if (certs.size() > 0) {
            LOGGER.debug(
                    "Found {} certificate(s) with keyUsage:digitalSignature",
                    certs.size());
            return (X509Certificate) certs.iterator().next();
        } else {
            LOGGER.debug("No certificates found.");
        }
        return findIssuer(store);
    }

    /**
     * Finds the certificate of the SCEP message object recipient.
     * 
     * @param store
     *            the certificate store to inspect.
     * @return the recipient's certificate.
     * @throws CertStoreException
     *             if the CertStore cannot be inspected
     */
    private static X509Certificate findRecipient(final CertStore store)
            throws CertStoreException {
        boolean[] keyUsage = new boolean[KEY_USAGE_LENGTH];
        keyUsage[2] = true;
        X509CertSelector signingSelector = new X509CertSelector();
        signingSelector.setBasicConstraints(ONLY_END_ENTITIES);
        signingSelector.setKeyUsage(keyUsage);

        LOGGER.debug("Selecting certificate with keyUsage:keyEncipherment");
        Collection<? extends Certificate> certs = store
                .getCertificates(signingSelector);
        if (certs.size() > 0) {
            LOGGER.debug(
                    "Found {} certificate(s) with keyUsage:keyEncipherment",
                    certs.size());
            return (X509Certificate) certs.iterator().next();
        } else {
            LOGGER.debug("No certificates found.");
        }

        LOGGER.debug("Selecting certificate with keyUsage:dataEncipherment");
        keyUsage = new boolean[KEY_USAGE_LENGTH];
        keyUsage[3] = true;
        signingSelector.setKeyUsage(keyUsage);

        certs = store.getCertificates(signingSelector);
        if (certs.size() > 0) {
            LOGGER.debug(
                    "Found {} certificate(s) with keyUsage:dataEncipherment",
                    certs.size());
            return (X509Certificate) certs.iterator().next();
        } else {
            LOGGER.debug("No certificates found");
        }

        return findIssuer(store);
    }
}
