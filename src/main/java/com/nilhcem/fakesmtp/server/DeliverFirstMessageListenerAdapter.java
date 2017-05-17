package org.subethamail.smtp.helper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.io.DeferredFileOutputStream;

/**
 * Delivers the mail only to the first recipient, to avoid multiple deliveries because of cc and bcc
 *
 * @author Pawel Baranik
 * @since 2017-05-17
 */
public class DeliverFirstMessageListenerAdapter implements MessageHandlerFactory {
    /**
     * 5 megs by default. The server will buffer incoming messages to disk
     * when they hit this limit in the DATA received.
     */
    private static int DEFAULT_DATA_DEFERRED_SIZE = 1024 * 1024 * 5;

    private Collection<SimpleMessageListener> listeners;
    private int dataDeferredSize;

    /**
     * Initializes this factory with a single listener.
     * <p>
     * Default data deferred size is 5 megs.
     */
    public DeliverFirstMessageListenerAdapter(SimpleMessageListener listener) {
        this(Collections.singleton(listener), DEFAULT_DATA_DEFERRED_SIZE);
    }

    /**
     * Initializes this factory with the listeners.
     * <p>
     * Default data deferred size is 5 megs.
     */
    public DeliverFirstMessageListenerAdapter(Collection<SimpleMessageListener> listeners) {
        this(listeners, DEFAULT_DATA_DEFERRED_SIZE);
    }

    /**
     * Initializes this factory with the listeners.
     *
     * @param dataDeferredSize The server will buffer
     *                         incoming messages to disk when they hit this limit in the
     *                         DATA received.
     */
    public DeliverFirstMessageListenerAdapter(Collection<SimpleMessageListener> listeners, int dataDeferredSize) {
        this.listeners = listeners;
        this.dataDeferredSize = dataDeferredSize;
    }

    /* (non-Javadoc)
     * @see org.subethamail.smtp.MessageHandlerFactory#create(org.subethamail.smtp.MessageContext)
     */
    public MessageHandler create(MessageContext ctx) {
        return new Handler(ctx);
    }

    /**
     * Needed by this class to track which listeners need delivery.
     */
    static class Delivery {
        SimpleMessageListener listener;

        public SimpleMessageListener getListener() {
            return this.listener;
        }

        String recipient;

        public String getRecipient() {
            return this.recipient;
        }

        public Delivery(SimpleMessageListener listener, String recipient) {
            this.listener = listener;
            this.recipient = recipient;
        }
    }

    /**
     * Class which implements the actual handler interface.
     */
    class Handler implements MessageHandler {
        MessageContext ctx;
        String from;
        List<Delivery> deliveries = new ArrayList<Delivery>();

        /** */
        public Handler(MessageContext ctx) {
            this.ctx = ctx;
        }

        /** */
        public void from(String from) throws RejectException {
            this.from = from;
        }

        /** */
        public void recipient(String recipient) throws RejectException {
            boolean addedListener = false;

            for (SimpleMessageListener listener : DeliverFirstMessageListenerAdapter.this.listeners) {
                if (listener.accept(this.from, recipient)) {
                    this.deliveries.add(new Delivery(listener, recipient));
                    addedListener = true;
                }
            }

            if (!addedListener)
                throw new RejectException(553, "<" + recipient + "> address unknown.");
        }

        /** */
        public void data(InputStream data) throws TooMuchDataException, IOException {
            Delivery delivery = this.deliveries.get(0);
            delivery.getListener().deliver(this.from, delivery.getRecipient(), data);
        }

        /** */
        public void done() {
        }
    }
}
