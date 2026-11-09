package dev.min.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Permission-gated contact cache; matching remains fast and synchronous on each key press. */
public final class ContactSearchProvider implements SearchProvider {
    public interface Listener {
        void onContactsChanged();
    }

    private static final class ContactEntry {
        final String name;
        final Uri uri;

        ContactEntry(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile List<ContactEntry> contacts = Collections.emptyList();
    private Listener listener;

    public ContactSearchProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void reload() {
        if (!isAvailable()) {
            contacts = Collections.emptyList();
            notifyChanged();
            return;
        }
        executor.execute(() -> {
            ArrayList<ContactEntry> loaded = new ArrayList<>();
            String[] projection = {ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME};
            try (Cursor cursor = context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    projection, null, null, ContactsContract.Contacts.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID);
                    int lookupColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY);
                    int nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME);
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(nameColumn);
                        String lookup = cursor.getString(lookupColumn);
                        if (name != null && lookup != null) {
                            Uri uri = ContactsContract.Contacts.getLookupUri(cursor.getLong(idColumn), lookup);
                            loaded.add(new ContactEntry(name, uri));
                        }
                    }
                }
            } catch (SecurityException ignored) {
                loaded.clear();
            }
            contacts = Collections.unmodifiableList(loaded);
            notifyChanged();
        });
    }

    private void notifyChanged() {
        Listener current = listener;
        if (current != null) {
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            main.post(current::onContactsChanged);
        }
    }

    @Override
    public String id() {
        return "contacts";
    }

    @Override
    public String label() {
        return "CONTACTS";
    }

    @Override
    public boolean isAvailable() {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public List<SearchResult> search(String query) {
        String normalized = FuzzyMatcher.normalize(query);
        if (normalized.isEmpty() || !isAvailable()) {
            return Collections.emptyList();
        }
        ArrayList<SearchResult> results = new ArrayList<>();
        for (ContactEntry contact : contacts) {
            FuzzyMatcher.Match match = FuzzyMatcher.match(normalized, contact.name);
            if (match != null) {
                results.add(SearchResult.contact(contact.name, contact.uri, match));
            }
        }
        Collections.sort(results, (left, right) -> {
            FuzzyMatcher.Match a = left.match;
            FuzzyMatcher.Match b = right.match;
            if (a.exact != b.exact) return a.exact ? -1 : 1;
            if (a.startsAtName != b.startsAtName) return a.startsAtName ? -1 : 1;
            if (a.startsAtWord != b.startsAtWord) return a.startsAtWord ? -1 : 1;
            if (a.position != b.position) return Integer.compare(a.position, b.position);
            if (left.title.length() != right.title.length()) return Integer.compare(left.title.length(), right.title.length());
            return left.title.compareToIgnoreCase(right.title);
        });
        return results;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
