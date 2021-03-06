/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mail;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;
import javax.mail.internet.MimeUtility;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.support.ExpressionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Expression} which can be used to split a {@link MailMessage}
 * per attachment. For example if a mail message has 5 attachments, then this
 * expression will return a <tt>List&lt;Message&gt;</tt> that contains 5 {@link Message}.
 * The message can be split 2 ways:
 * <table>
 *   <tr>
 *     <td>As an attachment</td>
 *     <td>
 *       The message is split into cloned messages, each has only one attachment.  The mail attachment in each message
 *       remains unprocessed.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>As a byte[]</td>
 *     <td>
 *       The attachments are split into new messages as the body. This allows the split messages to be easily used by
 *       other processors / routes, as many other camel components can work on the byte[], e.g. it can be written to disk
 *       using camel-file.
 *     </td>
 *   </tr>
 * </table>
 *
 * In both cases the attachment name is written to a the camel header &quot;CamelSplitAttachmentId&quot;
 */
public class TempAttachSplitter extends ExpressionAdapter {
    static final Logger logger = LoggerFactory.getLogger("TempAttachSplitter");

    
    public static final String HEADER_NAME = "CamelSplitAttachmentId";

    
    private boolean extractAttachments=true;

    public TempAttachSplitter() {
    }

    public TempAttachSplitter(boolean extractAttachments) {
        this.extractAttachments = extractAttachments;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        // must use getAttachments to ensure attachments is initial populated
        
        logger.info("In split attachment");
        
        if (exchange.getIn().getAttachments().isEmpty()) {
            return null;
        }

        try {
            List<Message> answer = new ArrayList<Message>();
            Message inMessage = exchange.getIn();
            for (Map.Entry<String, DataHandler> entry : inMessage.getAttachments().entrySet()) {
                Message attachmentMessage;
                if (extractAttachments) {
                    attachmentMessage = extractAttachment(inMessage, entry.getKey());
                } else {
                    attachmentMessage = splitAttachment(inMessage, entry.getKey(), entry.getValue());
                }

                if (attachmentMessage != null) {
                    answer.add(attachmentMessage);
                }
            }

            return answer;
        } catch (Exception e) {
            throw new RuntimeCamelException("Unable to split attachments from MimeMultipart message", e);
        }
    }

    private Message splitAttachment(Message inMessage, String attachmentName, DataHandler attachmentHandler) {
        final Message copy = inMessage.copy();
        Map<String, DataHandler> attachments = copy.getAttachments();
        attachments.clear();
        attachments.put(attachmentName, attachmentHandler);
        copy.setHeader(HEADER_NAME, attachmentName);
        return copy;
    }

    private Message extractAttachment(Message inMessage, String attachmentName) throws Exception {
        final Message outMessage = inMessage.copy();
        
                
        Object attachment = inMessage.getAttachment(attachmentName).getContent();
        
        logger.info("Attach name:"+attachmentName);
        
        
        
        if(attachmentName.indexOf("utf-8")>=0)
        {
            attachmentName=MimeUtility.decodeText(attachmentName);
            logger.info("Converting 1:"+attachmentName);
            logger.info("Converting 2:"+Normalizer.normalize(attachmentName, Normalizer.Form.NFC)); 
            
        }
        outMessage.setHeader(HEADER_NAME, attachmentName);
        
        logger.info("CLASS="+attachment.getClass().getName());
        
        
        if (attachment instanceof InputStream) {
            outMessage.setBody(readMimePart((InputStream) attachment));
            return outMessage;
        }
        
        if (attachment instanceof java.lang.String) {
            outMessage.setBody(attachment);
            return outMessage;
        }
        return null;
    }


    private byte[] readMimePart(InputStream mimePartStream) throws Exception {
        //  mimePartStream could be base64 encoded, or not, but we don't need to worry about it as
        // camel is smart enough to wrap it in a decoder stream (eg Base64DecoderStream) when required
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int len;
        byte[] buf = new byte[1024];
        while ((len = mimePartStream.read(buf, 0, 1024)) != -1) {
            bos.write(buf, 0, len);
        }
        mimePartStream.close();
        return bos.toByteArray();
    }


    public boolean isExtractAttachments() {
        return extractAttachments;
    }

    public void setExtractAttachments(boolean extractAttachments) {
        this.extractAttachments = extractAttachments;
    }
}