package com.example.bacafotoektp;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    public NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "ndak punya NFC, ndeso!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, this.getClass())
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled())
                showWirelessSettings();

            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    private void showWirelessSettings() {
        Toast.makeText(this, "nyalain NFCnya, gan!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Toast.makeText(this, "bentar yak ane baca fotonya dulu", Toast.LENGTH_SHORT).show();
        setIntent(intent);
        resolveIntent(intent);
    }

    private short bytesToShort(byte hi,byte lo) {
        return (short) (hi << 8 | lo & 0xFF);
    }

    private void resolveIntent(Intent intent) {
        try {
            String action = intent.getAction();

            if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                    || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                    || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
                Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                IsoDep tag = IsoDep.get(tagFromIntent);
                if (tag != null) {
                    tag.connect();

                    byte[] result;
                    byte[] selectMF = {(byte) 0x00, (byte) 0xA4, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x7F, (byte) 0x0A};
                    byte[] selectEFPhoto = {(byte) 0x00, (byte) 0xA4, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x6F, (byte) 0xF2};
                    byte[] readImageLength = {(byte) 0x00, (byte) 0xB0, (byte) 0x00, (byte) 0x00, (byte) 0x02};

                    result = tag.transceive(selectMF);
                    if (result[0] != (byte) 0x90 || result[1] != (byte) 0x00)
                        throw new IOException("raiso select MF: " + String.format("%04X", bytesToShort(result[0],result[1])));
                    result = tag.transceive(selectEFPhoto);
                    if (result[0] != (byte) 0x90 || result[1] != (byte) 0x00)
                        throw new IOException("raiso select EF Photo: " + String.format("%04X", bytesToShort(result[0],result[1])));
                    result = tag.transceive(readImageLength);
                    if (result.length != 4 || result[2] != (byte) 0x90 || result[3] != (byte) 0x00)
                        throw new IOException("raiso baca ukuran data foto: " + String.format("%04X", bytesToShort(result[0],result[1])));

                    short imageLength = bytesToShort(result[0],result[1]);
                    byte[] imageData = new byte[imageLength];

                    byte chunkSize = 16; // katanya ada reader mble'e yang mentok2 cuma bisa 16 byte sekali baca
                    short bytesRead = 2;
                    while (bytesRead < imageLength) {
                        short offset = bytesRead;
                        byte[] readChunk = {(byte) 0x00, (byte) 0xB0, (byte) (offset >> 8), (byte) (offset & 0x00FF), chunkSize};
                        result = tag.transceive(readChunk);
                        if (result[result.length - 2] != (byte) 0x90 || result[result.length - 1] != (byte) 0x00) {
                            throw new IOException("gagal baca data foto di offset " + offset + ": "
                                    + String.format("%04X", bytesToShort(result[0],result[1]))
                                    + " APDU: " + String.format("%02X%02X%02X%02X%02X",readChunk[0],readChunk[1],readChunk[2],readChunk[3],readChunk[4])
                            );
                        }
                        int dataCount = result.length - 2;
                        int imgDataOffset = offset - 2;
                        for (int i = 0; i < dataCount; i++) {
                            if (imgDataOffset + i >= imageData.length) break; // anti rempong index out of bounds
                            imageData[imgDataOffset + i] = (byte) (result[i] & 0xFF);
                        }
                        bytesRead += dataCount;
                    }

                    File sdCard = Environment.getExternalStorageDirectory();
                    File dir = new File (sdCard.getAbsolutePath() + "/Download");
                    dir.mkdirs();
                    File f = new File(dir, "foto-ektp.jpg");
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(imageData);
                    fos.close();

                    TextView message = (TextView) findViewById(R.id.message);
                    message.setText("cek foto di folder Download, namanya foto-ektp.jpg");
                }
            }
        } catch (Exception e) {
            String msg = "Ngeror gan!\n";
            StackTraceElement[] stes = e.getStackTrace();
            for (StackTraceElement ste: stes) {
                msg += ste.getMethodName() + "@" + ste.getFileName() + ":" + ste.getLineNumber() + "\n";
            }

            TextView message = (TextView) findViewById(R.id.message);
            message.setText(e.getClass().getName() + ": " + e.getMessage() + "\n" + msg);
        }
    }
}
