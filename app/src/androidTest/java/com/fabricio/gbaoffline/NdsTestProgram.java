package com.fabricio.gbaoffline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Original homebrew. Main screen is red, or green after reading EEPROM byte 0x5a.
 * ARM7 polls the stylus contact and ARM9 colors the lower screen green while touched.
 * A writes EEPROM through AUX SPI. No Nintendo logo, BIOS, or commercial game data.
 */
final class NdsTestProgram {
    static byte[] create() {
        int[] arm9 = {0xe59fd13c,0xe59f013c,0xe59f113c,0xe1c010b0,0xe59f0138,0xe3a01080,0xe5c01000,0xe59f0130,
            0xe3a01802,0xe5801000,0xe59f0128,0xe3a01801,0xe5801000,0xeb000024,0xe350005a,0xe59f1118,
            0x059f1118,0xe59f0118,0xe59f2118,0xe0c010b2,0xe2522001,0x1afffffc,0xe59f010c,0xe5901000,
            0xe3510000,0xe59f2104,0x159f2104,0xe59f0104,0xe1c020b0,0xe59f0100,0xe1d000b0,0xe3100001,
            0x1afffff4,0xeb00001c,0xeafffff2,0xe59f40ec,0xe59f50ec,0xe1c450b0,0xe12fff1e,0xe59f50e4,
            0xe1c450b0,0xeaffffff,0xe1c400b2,0xe1d450b0,0xe3150080,0x1afffffc,0xe1d400b2,0xe12fff1e,
            0xe3a05000,0xe1c450b0,0xe12fff1e,0xe92d4070,0xebffffed,0xe3a00003,0xebfffff2,0xe3a00000,
            0xebfffff0,0xe3a00000,0xebffffeb,0xe1a06000,0xebfffff2,0xe1a00006,0xe8bd8070,0xe92d4070,
            0xebffffe1,0xe3a00006,0xebffffe3,0xebffffeb,0xebffffdd,0xe3a00002,0xebffffe2,0xe3a00000,
            0xebffffe0,0xe3a0005a,0xebffffdb,0xebffffe3,0xebffffd5,0xe3a00005,0xebffffd7,0xebffffdf,
            0xe8bd8070,0x023ff000,0x04000304,0x00008203,0x04000240,0x04000000,0x04001000,0x0000801f,
            0x000083e0,0x06800000,0x0000c000,0x02001000,0x00007c00,0x000003e0,0x05000400,0x04000130,
            0x040001a0,0x0000a040,0x0000a000};
        int[] arm7 = {0xe59f0018,0xe59f1018,0xe1d020b0,0xe3120040,0x03a03001,0x13a03000,0xe5813000,0xeafffff9,
            0x04000136,0x02001000};
        byte[] rom=new byte[4096];
        ByteBuffer out=ByteBuffer.wrap(rom).order(ByteOrder.LITTLE_ENDIAN);
        byte[] name="FIRE DS TEST".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(name,0,rom,0,name.length);
        out.putInt(0x20,512);out.putInt(0x24,0x02000000);out.putInt(0x28,0x02000000);out.putInt(0x2c,arm9.length*4);
        out.putInt(0x30,2048);out.putInt(0x34,0x03800000);out.putInt(0x38,0x03800000);out.putInt(0x3c,arm7.length*4);
        out.putInt(0x80,rom.length);out.putInt(0x84,512);
        for(int i=0;i<arm9.length;i++)out.putInt(512+i*4,arm9[i]);
        for(int i=0;i<arm7.length;i++)out.putInt(2048+i*4,arm7[i]);
        int crc=0xffff;
        for(int i=0;i<0x15e;i++){crc^=rom[i]&255;for(int bit=0;bit<8;bit++)crc=(crc>>>1)^((crc&1)!=0?0xa001:0);}
        out.putShort(0x15e,(short)crc);
        return rom;
    }
}
