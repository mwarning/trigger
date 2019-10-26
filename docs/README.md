# Additional documentation

The status of the door is determined if the https query or ssh command returns the strings:

  * LOCKED (door closed)
  * UNLOCKED (door open)
  * or some other output (unknown door state)

The return message is displayed in the App for a short period.

Sample shell script that does not actually control a door/lock but that can be used for testing or as a template:

    #!/bin/sh
    # Lock demo, suitable for use with ssh mode of https://github.com/mwarning/trigger


    COMMAND=$1

    case ${COMMAND} in
        lock | close | shut)
            # issue lock command
            # assume it was successful and report locked
            echo LOCKED
            ;;

        open | unlock)
            # issue unlock command
            # assume it was successful and report locked
            echo UNLOCKED
            ;;

        status | *)
            # issue status command
            # assume it was successful and default/report locked
            echo LOCKED
            ;;

    esac

    # Trigger 1.7.1 will show correct image assuming the above is clean
    # debug or additional information can then be displayed below
    # it shows up in a Toast style message
    # Date/time is a useful addition
    #echo debug test
    date

See https://github.com/clach04/shell_locked for an example to control gpio pins on a Raspberry Pi.
