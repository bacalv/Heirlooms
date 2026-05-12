-- Allow deleting a plot even when flows target it (cascade to flows).
ALTER TABLE flows
    DROP CONSTRAINT flows_target_plot_id_fkey,
    ADD CONSTRAINT flows_target_plot_id_fkey
        FOREIGN KEY (target_plot_id) REFERENCES plots(id) ON DELETE CASCADE;
