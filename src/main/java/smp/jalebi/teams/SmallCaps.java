package smp.jalebi.teams;

public final class SmallCaps {
    private static final String MAP = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {}

    public static String of(String in) {
        StringBuilder sb = new StringBuilder();
        for (char c : in.toCharArray()) {
            char l = Character.toLowerCase(c);
            if (l >= 'a' && l <= 'z') sb.append(MAP.charAt(l - 'a'));
            else sb.append(c);
        }
        return sb.toString();
    }
}
