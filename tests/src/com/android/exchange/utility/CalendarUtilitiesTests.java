/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.exchange.utility;

import com.android.email.R;
import com.android.email.mail.Address;
import com.android.email.provider.EmailContent.Account;
import com.android.email.provider.EmailContent.Attachment;
import com.android.email.provider.EmailContent.Message;

import android.content.ContentValues;
import android.content.Entity;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Events;
import android.test.AndroidTestCase;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Tests of EAS Calendar Utilities
 * You can run this entire test case with:
 *   runtest -c com.android.exchange.utility.CalendarUtilitiesTests email
 *
 * Please see RFC2445 for RRULE definition
 * http://www.ietf.org/rfc/rfc2445.txt
 */

public class CalendarUtilitiesTests extends AndroidTestCase {

    // Some prebuilt time zones, Base64 encoded (as they arrive from EAS)
    // More time zones to be added over time

    // Not all time zones are appropriate for testing.  For example, ISRAEL_STANDARD_TIME cannot be
    // used because DST is determined from year to year in a non-standard way (related to the lunar
    // calendar); therefore, the test would only work during the year in which it was created
    private static final String INDIA_STANDARD_TIME =
        "tv7//0kAbgBkAGkAYQAgAFMAdABhAG4AZABhAHIAZAAgAFQAaQBtAGUAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEkAbgBkAGkAYQAgAEQAYQB5AGwAaQBnAGgAdAAgAFQAaQBtAGUA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
    private static final String PACIFIC_STANDARD_TIME =
        "4AEAAFAAYQBjAGkAZgBpAGMAIABTAHQAYQBuAGQAYQByAGQAIABUAGkAbQBlAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAsAAAABAAIAAAAAAAAAAAAAAFAAYQBjAGkAZgBpAGMAIABEAGEAeQBsAGkAZwBoAHQAIABUAGkA" +
        "bQBlAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMAAAACAAIAAAAAAAAAxP///w==";

    public void testGetSet() {
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};

        // First, check that getWord/Long are properly little endian
        assertEquals(0x0100, CalendarUtilities.getWord(bytes, 0));
        assertEquals(0x03020100, CalendarUtilities.getLong(bytes, 0));
        assertEquals(0x07060504, CalendarUtilities.getLong(bytes, 4));

        // Set some words and longs
        CalendarUtilities.setWord(bytes, 0, 0xDEAD);
        CalendarUtilities.setLong(bytes, 2, 0xBEEFBEEF);
        CalendarUtilities.setWord(bytes, 6, 0xCEDE);

        // Retrieve them
        assertEquals(0xDEAD, CalendarUtilities.getWord(bytes, 0));
        assertEquals(0xBEEFBEEF, CalendarUtilities.getLong(bytes, 2));
        assertEquals(0xCEDE, CalendarUtilities.getWord(bytes, 6));
    }

    public void testParseTimeZoneEndToEnd() {
        TimeZone tz = CalendarUtilities.tziStringToTimeZone(PACIFIC_STANDARD_TIME);
        assertEquals("Pacific Standard Time", tz.getDisplayName());
        tz = CalendarUtilities.tziStringToTimeZone(INDIA_STANDARD_TIME);
        assertEquals("India Standard Time", tz.getDisplayName());
    }

    public void testGenerateEasDayOfWeek() {
        String byDay = "TU;WE;SA";
        // TU = 4, WE = 8; SA = 64;
        assertEquals("76", CalendarUtilities.generateEasDayOfWeek(byDay));
        // MO = 2, TU = 4; WE = 8; TH = 16; FR = 32
        byDay = "MO;TU;WE;TH;FR";
        assertEquals("62", CalendarUtilities.generateEasDayOfWeek(byDay));
        // SU = 1
        byDay = "SU";
        assertEquals("1", CalendarUtilities.generateEasDayOfWeek(byDay));
    }

    public void testTokenFromRrule() {
        String rrule = "FREQ=DAILY;INTERVAL=1;BYDAY=WE,TH,SA;BYMONTHDAY=17";
        assertEquals("DAILY", CalendarUtilities.tokenFromRrule(rrule, "FREQ="));
        assertEquals("1", CalendarUtilities.tokenFromRrule(rrule, "INTERVAL="));
        assertEquals("17", CalendarUtilities.tokenFromRrule(rrule, "BYMONTHDAY="));
        assertNull(CalendarUtilities.tokenFromRrule(rrule, "UNTIL="));
    }

    public void testRecurrenceUntilToEasUntil() {
        // Test full formatCC
        assertEquals("YYYY-MM-DDTHH:MM:SS.000Z",
                CalendarUtilities.recurrenceUntilToEasUntil("YYYYMMDDTHHMMSSZ"));
        // Test date only format
        assertEquals("YYYY-MM-DDT00:00:00.000Z",
                CalendarUtilities.recurrenceUntilToEasUntil("YYYYMMDD"));
    }

    public void testParseEmailDateTimeToMillis(String date) {
        // Format for email date strings is 2010-02-23T16:00:00.000Z
        String dateString = "2010-02-23T15:16:17.000Z";
        long dateTime = CalendarUtilities.parseEmailDateTimeToMillis(dateString);
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(dateTime);
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        assertEquals(cal.get(Calendar.YEAR), 2010);
        assertEquals(cal.get(Calendar.MONTH), 1);  // 0 based
        assertEquals(cal.get(Calendar.DAY_OF_MONTH), 23);
        assertEquals(cal.get(Calendar.HOUR_OF_DAY), 16);
        assertEquals(cal.get(Calendar.MINUTE), 16);
        assertEquals(cal.get(Calendar.SECOND), 17);
    }

    public void testParseDateTimeToMillis(String date) {
        // Format for calendar date strings is 20100223T160000000Z
        String dateString = "20100223T151617000Z";
        long dateTime = CalendarUtilities.parseDateTimeToMillis(dateString);
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(dateTime);
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        assertEquals(cal.get(Calendar.YEAR), 2010);
        assertEquals(cal.get(Calendar.MONTH), 1);  // 0 based
        assertEquals(cal.get(Calendar.DAY_OF_MONTH), 23);
        assertEquals(cal.get(Calendar.HOUR_OF_DAY), 16);
        assertEquals(cal.get(Calendar.MINUTE), 16);
        assertEquals(cal.get(Calendar.SECOND), 17);
    }

    public void testCreateMessageForEntity_Reply() {
        // Create an Entity for an Event
        ContentValues entityValues = new ContentValues();
        Entity entity = new Entity(entityValues);

        // Set up values for the Event
        String attendee = "attendee@server.com";
        String organizer = "organizer@server.com";
        String location = "Meeting Location";
        String title = "Discuss Unit Tests";

        // Fill in times, location, title, and organizer
        entityValues.put("DTSTAMP",
                CalendarUtilities.convertEmailDateTimeToCalendarDateTime("2010-04-05T14:30:51Z"));
        entityValues.put(Events.DTSTART,
                CalendarUtilities.parseEmailDateTimeToMillis("2010-04-12T18:30:00Z"));
        entityValues.put(Events.DTEND,
                CalendarUtilities.parseEmailDateTimeToMillis("2010-04-12T19:30:00Z"));
        entityValues.put(Events.EVENT_LOCATION, location);
        entityValues.put(Events.TITLE, title);
        entityValues.put(Events.ORGANIZER, organizer);

        // Add the attendee
        ContentValues attendeeValues = new ContentValues();
        attendeeValues.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
        attendeeValues.put(Attendees.ATTENDEE_EMAIL, attendee);
        entity.addSubValue(Attendees.CONTENT_URI, attendeeValues);

        // Add the organizer
        ContentValues organizerValues = new ContentValues();
        organizerValues.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER);
        organizerValues.put(Attendees.ATTENDEE_EMAIL, organizer);
        entity.addSubValue(Attendees.CONTENT_URI, organizerValues);

        String uid = "31415926535";
        Account account = new Account();

        // The attendee is responding
        account.mEmailAddress = attendee;

        // Create the outgoing message
        Message msg = CalendarUtilities.createMessageForEntity(mContext, entity,
                Message.FLAG_OUTGOING_MEETING_ACCEPT, uid, account);

        // First, we should have a message
        assertNotNull(msg);

        // Now check some of the fields of the message
        assertEquals(Address.pack(new Address[] {new Address(organizer)}), msg.mTo);
        String accept = getContext().getResources().getString(R.string.meeting_accepted);
        assertEquals(accept + ": " + title, msg.mSubject);

        // And make sure we have an attachment
        assertNotNull(msg.mAttachments);
        assertEquals(1, msg.mAttachments.size());
        Attachment att = msg.mAttachments.get(0);
        // And that the attachment has the correct elements
        assertEquals("invite.ics", att.mFileName);
        assertEquals(Attachment.FLAG_SUPPRESS_DISPOSITION,
                att.mFlags & Attachment.FLAG_SUPPRESS_DISPOSITION);
        assertEquals("text/calendar; method=REPLY", att.mMimeType);
        assertNotNull(att.mContent);

        //TODO Check the contents of the attachment using an iCalendar parser
    }

    // Tests in progress...

//    public void testTimeZoneToTziString() {
//        for (String timeZoneId: TimeZone.getAvailableIDs()) {
//            TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
//            if (timeZone != null) {
//                String tzs = CalendarUtilities.timeZoneToTziString(timeZone);
//                TimeZone newTimeZone = CalendarUtilities.tziStringToTimeZone(tzs);
//                System.err.println("In: " + timeZone.getDisplayName() + ", Out: " + newTimeZone.getDisplayName());
//            }
//        }
//     }
//    public void testParseTimeZone() {
//        GregorianCalendar cal = getTestCalendar(parsedTimeZone, dstStart);
//        cal.add(GregorianCalendar.MINUTE, -1);
//        Date b = cal.getTime();
//        cal.add(GregorianCalendar.MINUTE, 2);
//        Date a = cal.getTime();
//        if (parsedTimeZone.inDaylightTime(b) || !parsedTimeZone.inDaylightTime(a)) {
//            userLog("ERROR IN TIME ZONE CONTROL!");
//        }
//        cal = getTestCalendar(parsedTimeZone, dstEnd);
//        cal.add(GregorianCalendar.HOUR, -2);
//        b = cal.getTime();
//        cal.add(GregorianCalendar.HOUR, 2);
//        a = cal.getTime();
//        if (!parsedTimeZone.inDaylightTime(b)) userLog("ERROR IN TIME ZONE CONTROL");
//        if (parsedTimeZone.inDaylightTime(a)) userLog("ERROR IN TIME ZONE CONTROL!");
//    }
}