package correcter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;

public class Main {
    static final String SEND_FILE_NAME = "send.txt";
    static final String ENCODED_FILE_NAME = "encoded.txt";
    static final String RECEIVED_FILE_NAME = "received.txt";
    static final String DECODED_FILE_NAME = "decoded.txt";
    static final List<Integer> checkBits = List.of(7, 6, 4);
    static final List<Integer> notCheckBits = List.of(1, 2, 3, 5);

    static String bytesToString(byte[] data, Function<Byte, String> transform) {
        StringJoiner res = new StringJoiner(" ");
        for (byte b : data) res.add(transform.apply(b));
        return res.toString();
    }

    static String toTextView(byte[] data) {
        return new String(data);
    }

    static String toHexView(byte[] data) {
        return bytesToString(data, b -> String.format("%02X", b));
    }

    static String toBinView(byte[] data) {
        return bytesToString(data, Main::byteToString);
    }

    static String byteToString(byte b) {
        char[] res = new char[8];
        for (int i = 0; i < 8; i++) res[i] = (char) ('0' + ((b >> (7 - i)) & 1));
        return new String(res);
    }

    static byte[] read(String fileName) {
        try {
            return Files.readAllBytes(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void write(String fileName, byte[] content) {
        try {
            Files.write(Paths.get(fileName), content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        System.out.print("Write a mode: ");
        String mode = in.next();
        System.out.println();
        switch (mode) {
            case "encode":
                Encoder.encode();
                break;
            case "send":
                Sender.send();
                break;
            case "decode":
                Decoder.decode();
                break;
            default:
                throw new RuntimeException("Unknown mode");
        }
    }

    static class Decoder {
        static byte correct(byte value) {
            if ((value & 1) == 1) return (byte) (value ^ 1);
            int res = 0;
            for (int k = 0; k < checkBits.size(); k++) {
                int checkBit = checkBits.get(k);
                int step = 1 << k;
                int x = 0;
                for (int i = checkBit; i >= 0; i -= step * 2) {
                    for (int j = 0; j < step; j++) {
                        x ^= value >> (i - j) & 1;
                    }
                }
                if (x == 1) res += 8 - checkBit;
            }
            value ^= 1 << (8 - res);
            return value;
        }

        static void correct(byte[] array) {
            for (int i = 0; i < array.length; i++) {
                array[i] = correct(array[i]);
            }
        }

        static byte decode(byte value) {
            byte res = 0;
            for (int i = 0; i < notCheckBits.size(); i++) {
                if (((value >> (notCheckBits.get(i))) & 1) == 1) {
                    res |= 1 << i;
                }
            }
            return res;
        }

        static byte[] decode(byte[] array) {
            byte[] res = new byte[array.length / 2];
            for (int i = 0; i < array.length; i += 2) {
                res[i / 2] = (byte) ((decode(array[i]) << 4) | decode(array[i + 1]));
            }
            return res;
        }

        static void decode() {
            byte[] data = read(RECEIVED_FILE_NAME);
            System.out.println(RECEIVED_FILE_NAME + ":");
            System.out.println("hex view: " + toHexView(data));
            System.out.println("bin view: " + toBinView(data));
            System.out.println();

            System.out.println(DECODED_FILE_NAME + ":");
            correct(data);
            System.out.println("correct: " + toBinView(data));
            byte[] decoded = decode(data);
            System.out.println("decode: " + toBinView(decoded));
            System.out.println("hex view: " + toHexView(decoded));
            System.out.println("text view: " + toTextView(decoded));
            write(DECODED_FILE_NAME, decoded);
        }
    }

    static class Sender {
        static final Random RND = new Random();

        static void corrupt(byte[] array) {
            for (int i = 0; i < array.length; i++) {
                array[i] ^= 1 << RND.nextInt(8);
            }
        }

        static void send() {
            byte[] data = read(ENCODED_FILE_NAME);
            System.out.println(ENCODED_FILE_NAME + ":");
            System.out.println("hex view: " + toHexView(data));
            System.out.println("bin view: " + toBinView(data));
            System.out.println();

            corrupt(data);
            write(RECEIVED_FILE_NAME, data);
            System.out.println(RECEIVED_FILE_NAME + ":");
            System.out.println("bin view: " + toBinView(data));
            System.out.println("hex view: " + toHexView(data));
        }
    }

    static class Encoder {
        static List<Integer> expandHamming(int datum) {
            List<Integer> res = new ArrayList<>();
            for (int i = 0; i < 8; i++) res.add(null);
            for (int i = 0; i < notCheckBits.size(); i++) {
                res.set(notCheckBits.get(i), (datum >> i) & 1);
            }
            return res;
        }

        static List<List<Integer>> expandHamming(byte[] data) {
            List<List<Integer>> res = new ArrayList<>(data.length * 2);
            for (byte datum : data) {
                res.add(expandHamming((datum >> 4) & 0xF));
                res.add(expandHamming(datum & 0xF));
            }
            return res;
        }

        static String expandedToString(List<List<Integer>> data) {
            return data.stream().map(
                    cur -> cur.stream().map(x -> x == null ? '.' : (char) ('0' + x))
                            .map(Objects::toString)
                            .collect(joining())
            ).map(s -> new StringBuilder(s).reverse().toString()).collect(joining(" "));
        }

        static byte addParity0(List<Integer> datum) {
            byte res = 0;
            for (int k = 0; k < checkBits.size(); k++) {
                int checkBit = checkBits.get(k);
                int step = 1 << k;
                int x = 0;
                for (int i = checkBit; i >= 0; i -= step * 2) {
                    for (int j = 0; j < step; j++) {
                        Integer d = datum.get(i - j);
                        x ^= d == null ? 0 : d;
                    }
                }
                if (x == 1) res |= 1 << checkBit;
            }
            for (int notCheckBit : notCheckBits) {
                if (datum.get(notCheckBit) != 0) {
                    res |= 1 << notCheckBit;
                }
            }
            return res;
        }

        static byte[] addParity(List<List<Integer>> data) {
            byte[] res = new byte[data.size()];
            for (int i = 0; i < data.size(); i++) res[i] = addParity0(data.get(i));
            return res;
        }

        static void encode() {
            byte[] data = read(SEND_FILE_NAME);
            System.out.println(SEND_FILE_NAME + ":");
            System.out.println("text view: " + toTextView(data));
            System.out.println("hex view: " + toHexView(data));
            System.out.println("bin view: " + toBinView(data));
            System.out.println();

            System.out.println(ENCODED_FILE_NAME + ":");
            List<List<Integer>> expanded = expandHamming(data);
            System.out.println("expand: " + expandedToString(expanded));
            byte[] withParity = addParity(expanded);
            System.out.println("parity: " + toBinView(withParity));
            System.out.println("hex view: " + toHexView(withParity));
            write(ENCODED_FILE_NAME, withParity);
        }
    }
}
