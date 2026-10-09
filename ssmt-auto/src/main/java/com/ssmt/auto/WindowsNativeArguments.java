package com.ssmt.auto;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Locale;

/** Reads original UTF-16 arguments only inside the explicitly configured native Auto launcher. */
@SuppressWarnings("restricted") // Trusted Win32 pointers; bounds follow the documented command-line limit.
final class WindowsNativeArguments {
    private WindowsNativeArguments() { }

    static String[] resolve(String[] supplied) {
        if (!Boolean.getBoolean("projectgo.auto.nativeArguments")
                || !System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return supplied.clone();
        }
        try (Arena arena = Arena.ofConfined()) {
            Path system = Path.of(java.util.Objects.requireNonNull(System.getenv("SystemRoot")), "System32");
            SymbolLookup kernel = SymbolLookup.libraryLookup(system.resolve("kernel32.dll"), arena);
            SymbolLookup shell = SymbolLookup.libraryLookup(system.resolve("shell32.dll"), arena);
            Linker linker = Linker.nativeLinker();
            var commandLine = linker.downcallHandle(kernel.find("GetCommandLineW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            var parse = linker.downcallHandle(shell.find("CommandLineToArgvW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            var free = linker.downcallHandle(kernel.find("LocalFree").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment count = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment text = (MemorySegment) commandLine.invokeExact();
            MemorySegment arguments = (MemorySegment) parse.invokeExact(text, count);
            if (arguments.address() == 0) { throw new IllegalStateException("Could not read native arguments"); }
            try {
                int length = count.get(ValueLayout.JAVA_INT, 0);
                if (length < 1 || length > 1024) { throw new IllegalStateException("Invalid native argument count"); }
                MemorySegment pointers = arguments.reinterpret(length * ValueLayout.ADDRESS.byteSize());
                String executable = wide(pointers.getAtIndex(ValueLayout.ADDRESS, 0));
                Path executableName = Path.of(executable).getFileName();
                if (executableName == null || !executableName.toString().equalsIgnoreCase("Project Go Auto.exe")) {
                    throw new IllegalStateException("Native argument mode requires the packaged Auto executable");
                }
                String[] result = new String[length - 1];
                for (int index = 1; index < length; index++) {
                    result[index - 1] = wide(pointers.getAtIndex(ValueLayout.ADDRESS, index));
                }
                return result;
            } finally {
                free.invoke(arguments);
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("Could not read original Windows Unicode arguments", exception);
        }
    }

    private static String wide(MemorySegment pointer) {
        MemorySegment bounded = pointer.reinterpret(65536);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < 32768; index++) {
            char character = bounded.getAtIndex(ValueLayout.JAVA_CHAR, index);
            if (character == 0) { return result.toString(); }
            result.append(character);
        }
        throw new IllegalStateException("Native argument exceeds Windows command-line limit");
    }
}
