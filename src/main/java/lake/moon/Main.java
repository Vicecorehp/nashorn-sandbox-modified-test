void main(String[] args) {
    if (args.length < 1) {
        System.out.println("No arguments provided");
        System.out.println("Program terminated!");
        return;
    }
    if (args.length != 1) {
        System.out.println("Wrong number of arguments provided");
        System.out.println("Program terminated!");
        return;
    }
    try {
        System.out.println("This process is aiming to presenting current java version");

        // argument injection warning lol
        var pb = new ProcessBuilder("java", "--%s".formatted(args[0]));
        var process = pb.start();

        var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        for (String line; (line = reader.readLine()) != null;) {
            System.out.println(line);
        }

        var exitCode = process.waitFor();
        System.out.println("Exit code: " + exitCode);

    } catch (IOException | InterruptedException e) {
        System.out.println(e.getMessage());
    }
}
