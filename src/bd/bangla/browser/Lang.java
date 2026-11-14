package bd.bangla.browser;

import android.content.SharedPreferences;

/** অ্যাপের দুই ভাষা: বাংলা (ডিফল্ট) আর ইংরেজি। */
public class Lang {

    private static boolean en = false;

    private static final java.util.HashMap<String, String> EN = new java.util.HashMap<>();

    static {
        EN.put(" চলছে", " running");
        EN.put(" ট্যাব খোলা যায়", " tabs at once");
        EN.put(" মুছবেন?", " delete?");
        EN.put(" মোছা হয়েছে", " deleted");
        EN.put("+ নতুন সাইট যোগ করুন", "+ Add a site");
        EN.put("/s · বাকি ", "/s · left ");
        EN.put("<a href='bangla://reload'>আবার চেষ্টা করুন</a></body></html>", "<a href='bangla://reload'>Try again</a></body></html>");
        EN.put("<body><div style='font-size:40px'>📡</div><h2>পেজ লোড হয়নি</h2>", "<body><div style='font-size:40px'>📡</div><h2>Page not loaded</h2>");
        EN.put("<p>ইন্টারনেট সংযোগ দেখুন, তারপর আবার চেষ্টা করুন।</p>", "<p>Check your internet connection, then try again.</p>");
        EN.put("অ", "A");
        EN.put("অনুবাদ", "Translate");
        EN.put("অ্যাপ", "App");
        EN.put("অ্যাপ সম্পর্কে", "About app");
        EN.put("অ্যাপের ভাষা", "App language");
        EN.put("আরও বড় (১৩০%)", "Larger (130%)");
        EN.put("ইংরেজি", "English");
        EN.put("ইংরেজি পেজ নিজে থেকেই বাংলায়", "English pages become Bengali automatically");
        EN.put("ইউটিউব", "YouTube");
        EN.put("ইতিহাস", "History");
        EN.put("ইতিহাস (", "History (");
        EN.put("ইতিহাস থেকে মুছুন", "Remove from history");
        EN.put("ইতিহাস মুছবেন?", "Clear history?");
        EN.put("ইতিহাস মুছুন", "Clear history");
        EN.put("ইতিহাস, বুকমার্ক, কুকি আর ক্যাশে সব মুছে যাবে। সেটিংস আগের মতো থাকবে।", "History, bookmarks, cookies and cache cleared. Settings stay the same.");
        EN.put("উইকিপিডিয়া", "Wikipedia");
        EN.put("এই পেজ বুকমার্ক করা যায় না", "This page cannot be bookmarked");
        EN.put("এই পেজ বুকমার্ক করুন", "Bookmark this page");
        EN.put("এই পেজের লিংক নেই", "This page has no link");
        EN.put("এই ফোনে প্রিন্ট চালু নেই", "Printing is not enabled on this phone");
        EN.put("এই ফোনে সেটিংস খোলা যায়নি", "Could not open settings on this phone");
        EN.put("একবারে সব ট্যাব বন্ধ করুন", "Close every tab at once");
        EN.put("এখনো কিছু নেই", "Nothing here yet");
        EN.put("এখনো কোনো ডাউনলোড নেই।\nকোনো পেজ থেকে ফাইল নামালে সেটা এখানে দেখা যাবে।", "No downloads yet.\nFiles you download from a page will show up here.");
        EN.put("ইতিহাস খালি।\nকোনো সাইটে গেলে সেটা এখানে জমা হবে।", "History is empty.\nSites you visit will be saved here.");
        EN.put("এটা ফিরিয়ে আনা যাবে না।", "This cannot be undone.");
        EN.put("ওয়েবপেজ বাংলায় অনুবাদ হচ্ছে", "Translating pages to Bengali");
        EN.put("ওয়েবপেজও কালো হয়ে যায়", "Web pages turn dark too");
        EN.put("কপি করা যায়নি", "Could not copy");
        EN.put("কম্পিউটারের মতো পেজ দেখায়", "Loads pages like a computer");
        EN.put("কালের কণ্ঠ", "Kaler Kantho");
        EN.put("কালো", "Always dark");
        EN.put("কুকি ও ক্যাশে মুছবেন?", "Clear cookies and cache?");
        EN.put("কুকি ও ক্যাশে মুছুন", "Clear cookies and cache");
        EN.put("কুকি ও ক্যাশে মোছা হয়েছে", "Cookies and cache cleared");
        EN.put("খুঁজুন", "Find");
        EN.put("চলছে", "Active");
        EN.put("চলছে (", "Active (");
        EN.put("চালু আছে", "On");
        EN.put("চেহারা", "Appearance");
        EN.put("ছবি লোড", "Load images");
        EN.put("ছোট (৮৫%)", "Small (85%)");
        EN.put("জাভাস্ক্রিপ্ট", "JavaScript");
        EN.put("জিমেইল", "Gmail");
        EN.put("জোর করে কালো", "Always dark");
        EN.put("ট্যাব", "Tabs");
        EN.put("ট্যাব ", "Tabs ");
        EN.put("ট্যাব (", "Tabs (");
        EN.put("ট্যাব বন্ধ হয়েছে", "Tab closed");
        EN.put("ঠিক আছে", "OK");
        EN.put("ঠিকানা (prothomalo.com)", "Address (prothomalo.com)");
        EN.put("ডাউনলোড", "Downloads");
        EN.put("ডাউনলোড অ্যাপ পাওয়া যায়নি", "No downloads app found");
        EN.put("ডাউনলোড দেখুন", "View downloads");
        EN.put("ডাউনলোড বাতিল হচ্ছে", "Download cancelled");
        EN.put("ডাউনলোড বাতিল হয়েছে", "Download cancelled");
        EN.put("ডাউনলোড শুরু হয়েছে: ", "Download started: ");
        EN.put("ডাউনলোড সম্পন্ন: ", "Download complete: ");
        EN.put("ডিফল্ট তালিকায় ফিরবেন?", "Reset to the default list?");
        EN.put("ডিফল্ট ব্রাউজার", "Default browser");
        EN.put("ডিফল্ট ব্রাউজার সেট হয়নি", "Default browser not set");
        EN.put("ডিফল্ট ব্রাউজার সেট হয়েছে", "Default browser set");
        EN.put("ডেটা", "Data");
        EN.put("ডেস্কটপ সাইট", "Desktop site");
        EN.put("তালিকা মুছবেন?", "Clear the list?");
        EN.put("থিম", "Theme");
        EN.put("থিম বদলালে পেজও বদলাবে", "Changing the theme changes pages too");
        EN.put("নতুন ট্যাব", "New tab");
        EN.put("নতুন ট্যাব খুলবে", "A fresh tab opens instead");
        EN.put("নতুন ট্যাবে খুলুন", "Open in new tab");
        EN.put("নতুন সাইট", "New site");
        EN.put("না", "No");
        EN.put("নাম (যেমন প্রথম আলো)", "Name (e.g. Prothom Alo)");
        EN.put("নেটওয়ার্ক সমস্যা, ফাইলটা নামানো যায়নি", "Network problem, the file could not be downloaded");
        EN.put("পিছনে", "Back");
        EN.put("পেজ", "Page");
        EN.put("পেজে খুঁজুন", "Find in page");
        EN.put("পেজে লেখা খুঁজুন", "Find text on page");
        EN.put("প্রথম আলো", "Prothom Alo");
        EN.put("প্রিন্ট", "Print");
        EN.put("প্রিয় সাইট", "Favourite sites");
        EN.put("ফাইল", "File");
        EN.put("ফাইল খোলার মতো অ্যাপ নেই", "No app can open this file");
        EN.put("ফাইল বাছুন", "Choose a file");
        EN.put("ফাইল রাখতে স্টোরেজের অনুমতি দাও, তারপর আবার চাপো", "Allow storage permission to save files, then try again");
        EN.put("ফাইল লেখা যাচ্ছে না", "Could not write the file");
        EN.put("ফাইলটা পাওয়া যায়নি", "File not found");
        EN.put("ফিরিয়ে আনো", "Reset");
        EN.put("ফেসবুক", "Facebook");
        EN.put("ফোনে ফাইল রাখার জায়গা পাওয়া যায়নি", "No space found on the phone to save the file");
        EN.put("বড় (১১৫%)", "Large (115%)");
        EN.put("বন্ধ", "Close");
        EN.put("বন্ধ আছে", "Off");
        EN.put("বাংলা", "Bengali");
        EN.put("ত্রুটি", "Error");
        EN.put("সার্চ", "Search");
        EN.put("নেটওয়ার্ক পরীক্ষা", "Network check");
        EN.put("সার্টিফিকেট সমস্যা", "Certificate problem");
        EN.put("এই সাইটের নিরাপত্তা সার্টিফিকেট ঠিক নেই। তবুও খুললে তথ্য ঝুঁকিতে থাকতে পারে।", "This site's security certificate is not valid. Opening it anyway can put your data at risk.");
        EN.put("তবুও খুলুন", "Open anyway");
        EN.put("দেখা হচ্ছে…", "Checking…");
        EN.put("চালু", "On");
        EN.put("বন্ধ", "Off");
        EN.put("বাংলা ব্রাউজার", "Bangla Browser");
        EN.put("বাংলা ব্রাউজার এখনই ডিফল্ট ব্রাউজার", "Bangla Browser is already the default browser");
        EN.put("বাংলানিউজ", "Banglanews24");
        EN.put("বাংলায় অনুবাদ", "Translate to Bengali");
        EN.put("বাতিল", "Cancel");
        EN.put("বিডিনিউজ২৪", "bdnews24");
        EN.put("বুকমার্ক", "Bookmarks");
        EN.put("বুকমার্ক মুছুন", "Clear bookmarks");
        EN.put("বুকমার্ক সরান", "Remove bookmark");
        EN.put("বুকমার্ক সরানো হয়েছে", "Bookmark removed");
        EN.put("বুকমার্কে রাখা হয়েছে", "Saved to bookmarks");
        EN.put("বের হতে আবার চাপুন", "Press again to exit");
        EN.put("ব্রাউজার চালু হলে আগের ট্যাব ফিরে আসে", "Your tabs come back when the browser opens");
        EN.put("ব্রাউজিং", "Browsing");
        EN.put("ভাষা", "Language");
        EN.put("ভাষা বদলাতে অ্যাপ আবার চালু হবে", "The app reloads when you change the language");
        EN.put("মুছুন", "Delete");
        EN.put("মুছে ফেলা হয়েছে", "Cleared");
        EN.put("মেনু", "Menu");
        EN.put("যোগ করুন", "Add");
        EN.put("রাতের", "Dark");
        EN.put("রাতের মোড", "Dark mode");
        EN.put("রিলোড", "Reload");
        EN.put("লিংক", "Link");
        EN.put("লিংক কপি", "Copy link");
        EN.put("লিংক কপি হয়েছে", "Link copied");
        EN.put("লিংক নতুন ট্যাবে", "Open links in new tab");
        EN.put("লেখার আকার", "Text size");
        EN.put("শুধু তালিকা থেকে মুছে যাবে, ফোনে নামানো ফাইল থাকবে।", "Only the list is cleared, files stay on the phone.");
        EN.put("শেয়ার", "Share");
        EN.put("শেয়ার করুন", "Share");
        EN.put("শেষ ট্যাব ফিরিয়ে আনুন", "Restore last tabs");
        EN.put("সংযোগ হচ্ছে…", "Connecting…");
        EN.put("সংস্করণ", "Version");
        EN.put("সংস্করণ ১.২.১.১২", "Version 1.3.0");
        EN.put("সব ট্যাব", "All tabs");
        EN.put("সব ট্যাব বন্ধ", "Close all tabs");
        EN.put("সব ট্যাব বন্ধ করবেন?", "Close all tabs?");
        EN.put("সব ট্যাব বন্ধ হয়েছে", "All tabs closed");
        EN.put("সব ট্যাব মুছে নতুন করে শুরু হবে।", "Every tab closes and a fresh one opens.");
        EN.put("সব ডেটা মুছবেন?", "Clear all data?");
        EN.put("সব ডেটা মুছুন", "Clear all data");
        EN.put("সব ডেটা মোছা হয়েছে", "All data cleared");
        EN.put("সব বন্ধ", "Close all");
        EN.put("সব মুছুন", "Clear all");
        EN.put("সব সাইট থেকে লগআউট হয়ে যাবে।", "You will be signed out of all sites.");
        EN.put("সম্পন্ন", "Done");
        EN.put("সম্পন্ন হয়েছে", "Completed");
        EN.put("সর্বোচ্চ ", "Maximum ");
        EN.put("সাইটের ঠিকানা লিখো", "Enter the site address");
        EN.put("সাধারণ", "General");
        EN.put("সামনে", "Forward");
        EN.put("সার্চ ইঞ্জিন", "Search engine");
        EN.put("সার্চ করুন বা ঠিকানা লিখুন", "Search or type address");
        EN.put("সার্ভার ফাইলটা দিচ্ছে না (", "Server is not sending the file (");
        EN.put("সিস্টেম", "System");
        EN.put("সিস্টেম সেটিং", "Follow system");
        EN.put("সিস্টেম সেটিং অনুযায়ী", "Follow system setting");
        EN.put("সেট আছে", "Already set");
        EN.put("সেট করুন", "Set now");
        EN.put("সেটিংস", "Settings");
        EN.put("সোর্স কোড", "Source code");
        EN.put("স্বাভাবিক (১০০%)", "Normal (100%)");
        EN.put("হোম", "Home");
        EN.put("১.২", "1.3.0");
        EN.put("১.২.১.১২", "1.3.0");
        EN.put("＋ নতুন ট্যাব", "+ New tab");
    }

    public static void init(SharedPreferences p) {
        en = p != null && "en".equals(p.getString("lang", "bn"));
    }

    public static boolean isEn() { return en; }

    /** বাংলা লেখা থাকলে সেটাই ফেরে; ইংরেজি বাছা থাকলে ইংরেজি অনুবাদ। */
    public static String t(String bn) {
        if (!en || bn == null) return bn;
        String v = EN.get(bn);
        return v == null ? bn : v;
    }

    /** "ইতিহাস মুছবেন?" / "Delete history?" */
    public static String deleteTitle(String label) {
        return en ? "Delete " + t(label).toLowerCase(java.util.Locale.US) + "?" : label + " মুছবেন?";
    }

    /** সুইচের পাশে "চালু" / "বন্ধ" — ইংরেজিতে "On" / "Off" */
    public static String onOff(boolean on) {
        return en ? (on ? "On" : "Off") : (on ? "চালু" : "বন্ধ");
    }

    /** "ইতিহাস মোছা হয়েছে" / "History deleted" */
    public static String deleted(String label) {
        return en ? t(label) + " deleted" : label + " মোছা হয়েছে";
    }
}
